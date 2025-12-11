package com.hanghae.ecommerce.infrastructure.coupon;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 쿠폰 발급 대기열 관리 서비스
 *
 * Redis List를 활용하여 선착순 쿠폰 발급 대기열을 관리합니다.
 *
 * ## 변경 사항 (리뷰 반영)
 * 1. SortedSet → List(Queue) 변경: 선착순 보장에 더 심플한 자료구조
 * 2. Redis quantity 관리 제거: DB를 단일 진실 공급원(Single Source of Truth)으로 사용
 *    - 복잡도 감소, 데이터 정합성 보장
 *
 * ## Redis 자료구조 설계
 *
 * ### 1. List (대기열) - FIFO Queue
 * - Key: `coupon:queue:{couponId}`
 * - Value: userId (String)
 * - 용도: 선착순 순서 보장 (RPUSH로 추가, LPOP으로 처리)
 *
 * ### 2. Set (발급 완료/대기 중 추적)
 * - Key: `coupon:issued:{couponId}` - 발급 완료 사용자
 * - Key: `coupon:pending:{couponId}` - 대기열에 있는 사용자 (중복 enqueue 방지)
 * - Member: userId (String)
 *
 * ## 동작 방식
 * 1. 사용자가 쿠폰 발급 요청 → 대기열에 추가 (RPUSH)
 * 2. 즉시 응답 반환 (비동기 처리)
 * 3. 스케줄러가 주기적으로 대기열을 처리하여 실제 발급 (LPOP)
 * 4. 수량 확인은 DB에서 직접 수행 (Redis에서 quantity 관리 안 함)
 */
@Service
public class CouponQueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 키 패턴
    private static final String QUEUE_KEY_PREFIX = "coupon:queue:";
    private static final String ISSUED_KEY_PREFIX = "coupon:issued:";
    private static final String PENDING_KEY_PREFIX = "coupon:pending:";

    public CouponQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 쿠폰 발급 대기열에 사용자 추가
     *
     * @param couponId      쿠폰 ID
     * @param userId        사용자 ID
     * @param couponEndDate 쿠폰 발급 종료일 (TTL 계산용, null이면 기본 7일)
     * @return 대기열 순위 (1부터 시작, -1이면 이미 발급됨 또는 대기열에 이미 존재)
     */
    public long enqueue(Long couponId, Long userId, LocalDateTime couponEndDate) {
        if (couponId == null || userId == null) {
            throw new IllegalArgumentException("쿠폰 ID와 사용자 ID는 null일 수 없습니다.");
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        String pendingKey = PENDING_KEY_PREFIX + couponId;
        String userIdStr = String.valueOf(userId);

        // 1. 이미 발급된 사용자인지 확인
        Boolean isIssued = redisTemplate.opsForSet().isMember(issuedKey, userIdStr);
        if (Boolean.TRUE.equals(isIssued)) {
            return -1; // 이미 발급됨
        }

        // 2. 이미 대기열에 있는지 확인 (pending set으로 O(1) 체크)
        Boolean isPending = redisTemplate.opsForSet().isMember(pendingKey, userIdStr);
        if (Boolean.TRUE.equals(isPending)) {
            return getQueueRank(couponId, userId);
        }

        // 3. pending set에 추가 (중복 enqueue 방지)
        redisTemplate.opsForSet().add(pendingKey, userIdStr);

        // 4. 대기열에 추가 (RPUSH - 뒤에 추가, FIFO)
        Long size = redisTemplate.opsForList().rightPush(queueKey, userIdStr);

        // 5. TTL 설정
        setTtl(couponId, couponEndDate);

        return size != null ? size : -1;
    }

    /**
     * 쿠폰 발급 대기열에 사용자 추가 (기본 TTL 사용)
     */
    public long enqueue(Long couponId, Long userId) {
        return enqueue(couponId, userId, null);
    }

    /**
     * 대기열에서 상위 N명 조회 (제거하지 않음)
     *
     * @param couponId 쿠폰 ID
     * @param limit    조회할 인원 수
     * @return 사용자 ID 목록 (선착순 순서)
     */
    public Set<Object> getTopUsers(Long couponId, long limit) {
        if (couponId == null || limit <= 0) {
            throw new IllegalArgumentException("쿠폰 ID는 null일 수 없고, limit은 1 이상이어야 합니다.");
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;

        // LRANGE: 0부터 limit-1까지 조회 (선착순)
        List<Object> users = redisTemplate.opsForList().range(queueKey, 0, limit - 1);

        return users != null ? new HashSet<>(users) : Set.of();
    }

    /**
     * 대기열에서 한 명 꺼내기 (LPOP - 선착순)
     *
     * @param couponId 쿠폰 ID
     * @return 사용자 ID (없으면 null)
     */
    public String dequeue(Long couponId) {
        if (couponId == null) {
            return null;
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        Object userId = redisTemplate.opsForList().leftPop(queueKey);

        return userId != null ? userId.toString() : null;
    }

    /**
     * 대기열에서 사용자 제거
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     */
    public void removeFromQueue(Long couponId, Long userId) {
        if (couponId == null || userId == null) {
            return;
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        String pendingKey = PENDING_KEY_PREFIX + couponId;
        String userIdStr = String.valueOf(userId);

        redisTemplate.opsForList().remove(queueKey, 1, userIdStr);
        redisTemplate.opsForSet().remove(pendingKey, userIdStr);
    }

    /**
     * 발급 완료 사용자로 등록
     *
     * @param couponId      쿠폰 ID
     * @param userId        사용자 ID
     * @param couponEndDate 쿠폰 발급 종료일 (TTL 계산용)
     */
    public void markAsIssued(Long couponId, Long userId, LocalDateTime couponEndDate) {
        if (couponId == null || userId == null) {
            return;
        }

        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        String pendingKey = PENDING_KEY_PREFIX + couponId;
        String userIdStr = String.valueOf(userId);

        // 발급 완료 Set에 추가
        redisTemplate.opsForSet().add(issuedKey, userIdStr);

        // pending에서 제거
        redisTemplate.opsForSet().remove(pendingKey, userIdStr);

        // TTL 설정
        Duration ttl = calculateTtl(couponEndDate);
        redisTemplate.expire(issuedKey, ttl);
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
     * @param userId   사용자 ID
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
     * @param userId   사용자 ID
     * @return 순위 (1부터 시작, 없으면 -1)
     */
    public long getQueueRank(Long couponId, Long userId) {
        if (couponId == null || userId == null) {
            return -1;
        }

        String queueKey = QUEUE_KEY_PREFIX + couponId;
        String userIdStr = String.valueOf(userId);

        // List에서 인덱스 찾기 (O(N) - 대기열이 크지 않으면 괜찮음)
        List<Object> queue = redisTemplate.opsForList().range(queueKey, 0, -1);
        if (queue == null) {
            return -1;
        }

        int index = queue.indexOf(userIdStr);
        return index >= 0 ? index + 1 : -1;
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
        Long size = redisTemplate.opsForList().size(queueKey);
        return size != null ? size : 0;
    }

    /**
     * TTL 설정 (대기열 + pending)
     */
    private void setTtl(Long couponId, LocalDateTime couponEndDate) {
        String queueKey = QUEUE_KEY_PREFIX + couponId;
        String pendingKey = PENDING_KEY_PREFIX + couponId;
        Duration ttl = calculateTtl(couponEndDate);
        redisTemplate.expire(queueKey, ttl);
        redisTemplate.expire(pendingKey, ttl);
    }

    /**
     * TTL 계산
     */
    private Duration calculateTtl(LocalDateTime couponEndDate) {
        if (couponEndDate != null) {
            LocalDateTime expireTime = couponEndDate.plusDays(1);
            LocalDateTime now = LocalDateTime.now();

            if (expireTime.isAfter(now)) {
                long seconds = java.time.Duration.between(now, expireTime).getSeconds();
                return Duration.ofSeconds(seconds);
            }
        }
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
        String pendingKey = PENDING_KEY_PREFIX + couponId;

        redisTemplate.delete(queueKey);
        redisTemplate.delete(issuedKey);
        redisTemplate.delete(pendingKey);
    }
}
