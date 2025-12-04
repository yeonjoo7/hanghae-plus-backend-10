package com.hanghae.ecommerce.infrastructure.coupon;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 쿠폰 발급 대기열 관리 서비스
 * 
 * Redis Sorted Set을 활용하여 선착순 쿠폰 발급 대기열을 관리합니다.
 * 
 * ## Redis 자료구조 설계
 * 
 * ### 1. Sorted Set (대기열)
 * - Key: `coupon:queue:{couponId}`
 * - Score: 요청 시간 (Unix timestamp in milliseconds)
 * - Member: userId (String)
 * - 용도: 선착순 순서 보장
 * 
 * ### 2. Set (발급 완료 추적)
 * - Key: `coupon:issued:{couponId}`
 * - Member: userId (String)
 * - 용도: 중복 발급 방지, 발급 완료 사용자 추적
 * 
 * ### 3. String (남은 수량)
 * - Key: `coupon:quantity:{couponId}`
 * - Value: 남은 수량 (Integer)
 * - 용도: 실시간 수량 관리
 * 
 * ## 동작 방식
 * 1. 사용자가 쿠폰 발급 요청 → 대기열에 추가 (ZADD)
 * 2. 즉시 응답 반환 (비동기 처리)
 * 3. 스케줄러가 주기적으로 대기열을 처리하여 실제 발급
 */
@Service
public class CouponQueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 키 패턴
    private static final String QUEUE_KEY_PREFIX = "coupon:queue:";
    private static final String ISSUED_KEY_PREFIX = "coupon:issued:";
    private static final String QUANTITY_KEY_PREFIX = "coupon:quantity:";

    public CouponQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 쿠폰 발급 대기열에 사용자 추가
     * 
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @param couponEndDate 쿠폰 발급 종료일 (TTL 계산용, null이면 기본 7일)
     * @return 대기열 순위 (1부터 시작, -1이면 이미 발급됨 또는 대기열에 이미 존재)
     */
    public long enqueue(Long couponId, Long userId, LocalDateTime couponEndDate) {
        if (couponId == null || userId == null) {
            throw new IllegalArgumentException("쿠폰 ID와 사용자 ID는 null일 수 없습니다.");
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        String userIdStr = String.valueOf(userId);

        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        // 1. 이미 발급된 사용자인지 확인
        Boolean isIssued = redisTemplate.opsForSet().isMember(issuedKey, userIdStr);
        if (Boolean.TRUE.equals(isIssued)) {
            return -1; // 이미 발급됨
        }

        // 2. 이미 대기열에 있는지 확인
        Double existingScore = zSetOps.score(queueKey, userIdStr);
        if (existingScore != null) {
            // 이미 대기열에 있으면 기존 순위 반환
            Long rank = zSetOps.rank(queueKey, userIdStr);
            return rank != null ? rank + 1 : -1; // 0-based를 1-based로 변환
        }

        // 3. 대기열에 추가 (Score = 현재 시간 milliseconds)
        long timestamp = Instant.now().toEpochMilli();
        zSetOps.add(queueKey, userIdStr, timestamp);

        // 4. TTL 설정 (쿠폰 종료일 + 여유 시간 1일)
        setQueueTtl(couponId, couponEndDate);

        // 5. 순위 조회
        Long rank = zSetOps.rank(queueKey, userIdStr);
        return rank != null ? rank + 1 : -1; // 0-based를 1-based로 변환
    }

    /**
     * 쿠폰 발급 대기열에 사용자 추가 (기본 TTL 사용)
     * 
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 대기열 순위 (1부터 시작, -1이면 이미 발급됨 또는 대기열에 이미 존재)
     */
    public long enqueue(Long couponId, Long userId) {
        return enqueue(couponId, userId, null);
    }

    /**
     * 대기열에서 상위 N명 조회
     * 
     * @param couponId 쿠폰 ID
     * @param limit 조회할 인원 수
     * @return 사용자 ID 목록 (선착순 순서)
     */
    public Set<Object> getTopUsers(Long couponId, long limit) {
        if (couponId == null || limit <= 0) {
            throw new IllegalArgumentException("쿠폰 ID는 null일 수 없고, limit은 1 이상이어야 합니다.");
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        // ZRANGE: 0부터 limit-1까지 조회 (선착순)
        return zSetOps.range(queueKey, 0, limit - 1);
    }

    /**
     * 대기열에서 사용자 제거
     * 
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     */
    public void removeFromQueue(Long couponId, Long userId) {
        if (couponId == null || userId == null) {
            return;
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        String userIdStr = String.valueOf(userId);
        redisTemplate.opsForZSet().remove(queueKey, userIdStr);
    }

    /**
     * 발급 완료 사용자로 등록
     * 
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @param couponEndDate 쿠폰 발급 종료일 (TTL 계산용)
     */
    public void markAsIssued(Long couponId, Long userId, LocalDateTime couponEndDate) {
        if (couponId == null || userId == null) {
            return;
        }

        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        String userIdStr = String.valueOf(userId);

        // Set에 추가
        redisTemplate.opsForSet().add(issuedKey, userIdStr);

        // TTL 설정
        setIssuedSetTtl(couponId, couponEndDate);

        // 대기열에서 제거
        removeFromQueue(couponId, userId);
    }

    /**
     * 발급 완료 사용자로 등록 (기본 TTL 사용)
     */
    public void markAsIssued(Long couponId, Long userId) {
        markAsIssued(couponId, userId, null);
    }

    /**
     * 사용자가 이미 발급받았는지 확인
     * 
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 여부
     */
    public boolean isAlreadyIssued(Long couponId, Long userId) {
        if (couponId == null || userId == null) {
            return false;
        }

        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        String userIdStr = String.valueOf(userId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(issuedKey, userIdStr));
    }

    /**
     * 대기열에서 사용자의 순위 조회
     * 
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 순위 (1부터 시작, 없으면 -1)
     */
    public long getQueueRank(Long couponId, Long userId) {
        if (couponId == null || userId == null) {
            return -1;
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        String userIdStr = String.valueOf(userId);
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        Long rank = zSetOps.rank(queueKey, userIdStr);
        return rank != null ? rank + 1 : -1; // 0-based를 1-based로 변환
    }

    /**
     * 대기열 크기 조회
     * 
     * @param couponId 쿠폰 ID
     * @return 대기열에 있는 사용자 수
     */
    public long getQueueSize(Long couponId) {
        if (couponId == null) {
            return 0;
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        Long size = redisTemplate.opsForZSet().zCard(queueKey);
        return size != null ? size : 0;
    }

    /**
     * 쿠폰 수량 초기화
     * 
     * @param couponId 쿠폰 ID
     * @param totalQuantity 총 수량
     * @param couponEndDate 쿠폰 발급 종료일 (TTL 계산용)
     */
    public void initializeQuantity(Long couponId, int totalQuantity, LocalDateTime couponEndDate) {
        if (couponId == null || totalQuantity <= 0) {
            return;
        }

        String quantityKey = QUANTITY_KEY_PREFIX + couponId;
        redisTemplate.opsForValue().set(quantityKey, totalQuantity);
        
        // TTL 설정
        setQuantityTtl(couponId, couponEndDate);
    }

    /**
     * 쿠폰 수량 초기화 (기본 TTL 사용)
     */
    public void initializeQuantity(Long couponId, int totalQuantity) {
        initializeQuantity(couponId, totalQuantity, null);
    }

    /**
     * 남은 수량 조회
     * 
     * @param couponId 쿠폰 ID
     * @return 남은 수량 (없으면 -1)
     */
    public int getRemainingQuantity(Long couponId) {
        if (couponId == null) {
            return -1;
        }

        String quantityKey = QUANTITY_KEY_PREFIX + couponId;
        Object value = redisTemplate.opsForValue().get(quantityKey);
        
        if (value == null) {
            return -1;
        }
        
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            return Integer.parseInt((String) value);
        } else {
            return -1;
        }
    }

    /**
     * 수량 차감 (원자적 연산)
     * 
     * @param couponId 쿠폰 ID
     * @return 차감 후 남은 수량 (차감 실패 시 -1)
     */
    public int decrementQuantity(Long couponId) {
        if (couponId == null) {
            return -1;
        }

        String quantityKey = QUANTITY_KEY_PREFIX + couponId;
        Long remaining = redisTemplate.opsForValue().decrement(quantityKey);
        
        return remaining != null ? remaining.intValue() : -1;
    }

    /**
     * 대기열 TTL 설정
     * 
     * @param couponId 쿠폰 ID
     * @param couponEndDate 쿠폰 종료일 (null이면 기본 7일)
     */
    private void setQueueTtl(Long couponId, LocalDateTime couponEndDate) {
        String queueKey = QUEUE_KEY_PREFIX + couponId;
        Duration ttl = calculateTtl(couponEndDate);
        redisTemplate.expire(queueKey, ttl);
    }

    /**
     * 발급 완료 Set TTL 설정
     */
    private void setIssuedSetTtl(Long couponId, LocalDateTime couponEndDate) {
        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        Duration ttl = calculateTtl(couponEndDate);
        redisTemplate.expire(issuedKey, ttl);
    }

    /**
     * 수량 키 TTL 설정
     */
    private void setQuantityTtl(Long couponId, LocalDateTime couponEndDate) {
        String quantityKey = QUANTITY_KEY_PREFIX + couponId;
        Duration ttl = calculateTtl(couponEndDate);
        redisTemplate.expire(quantityKey, ttl);
    }

    /**
     * TTL 계산
     * 쿠폰 종료일 + 여유 시간(1일)을 기준으로 TTL을 계산합니다.
     * 
     * @param couponEndDate 쿠폰 종료일 (null이면 기본 7일)
     * @return TTL Duration
     */
    private Duration calculateTtl(LocalDateTime couponEndDate) {
        if (couponEndDate != null) {
            LocalDateTime expireTime = couponEndDate.plusDays(1); // 쿠폰 종료일 + 1일 여유
            LocalDateTime now = LocalDateTime.now();
            
            if (expireTime.isAfter(now)) {
                long seconds = java.time.Duration.between(now, expireTime).getSeconds();
                return Duration.ofSeconds(seconds);
            }
        }
        
        // 기본값: 7일 (쿠폰 종료일이 없거나 이미 지난 경우)
        return Duration.ofDays(7);
    }

    /**
     * 쿠폰 대기열 초기화 (테스트용)
     * 
     * @param couponId 쿠폰 ID
     */
    public void clearQueue(Long couponId) {
        if (couponId == null) {
            return;
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        String quantityKey = QUANTITY_KEY_PREFIX + couponId;

        redisTemplate.delete(queueKey);
        redisTemplate.delete(issuedKey);
        redisTemplate.delete(quantityKey);
    }
}

