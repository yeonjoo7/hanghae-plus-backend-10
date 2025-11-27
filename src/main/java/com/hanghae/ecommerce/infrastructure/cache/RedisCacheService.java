package com.hanghae.ecommerce.infrastructure.cache;

import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 캐시 서비스
 * 
 * Cache Stampede 방지를 위한 분산락 기반 캐시 갱신 전략을 제공합니다.
 * 
 * ## 방지 전략
 * 1. 분산락(Distributed Lock): 캐시 갱신 시 락을 획득한 하나의 요청만 DB 조회
 * 2. 이중 체크(Double-Check): 락 획득 후 캐시 재확인
 * 3. Stale-While-Revalidate: 만료된 캐시를 즉시 반환하고 백그라운드에서 갱신 (선택적)
 */
@Service
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LockManager lockManager;
    
    // 캐시 갱신 락 타임아웃
    private static final long CACHE_LOCK_TIMEOUT_SECONDS = 5L;
    
    // 캐시 갱신 락 키 접두사
    private static final String CACHE_LOCK_PREFIX = "cache-lock:";

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate, LockManager lockManager) {
        this.redisTemplate = redisTemplate;
        this.lockManager = lockManager;
    }

    /**
     * 캐시에서 값을 조회합니다.
     * 
     * @param key 캐시 키
     * @param type 반환 타입
     * @return 캐시된 값 또는 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 캐시에 값을 저장합니다.
     * 
     * @param key 캐시 키
     * @param value 저장할 값
     * @param ttl 만료 시간
     */
    public void set(String key, Object value, Duration ttl) {
        if (value != null) {
            redisTemplate.opsForValue().set(key, value, ttl);
        }
    }

    /**
     * 캐시를 삭제합니다.
     * 
     * @param key 캐시 키
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 패턴에 맞는 캐시를 모두 삭제합니다.
     * 
     * @param pattern 키 패턴 (예: "product:*")
     */
    public void deleteByPattern(String pattern) {
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Cache Aside 패턴 + Cache Stampede 방지
     * 
     * 캐시에서 조회하고, 없으면 DB에서 조회하여 캐시에 저장합니다.
     * 분산락을 사용하여 동시에 여러 요청이 DB를 조회하는 것을 방지합니다.
     * 
     * @param key 캐시 키
     * @param type 반환 타입
     * @param ttl 캐시 TTL
     * @param loader DB 조회 함수
     * @return 캐시된 값 또는 DB에서 조회한 값
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        // 1. 캐시에서 먼저 조회 (Cache Hit)
        T cachedValue = get(key, type);
        if (cachedValue != null) {
            return cachedValue;
        }

        // 2. 캐시 미스 - 분산락을 사용하여 DB 조회 (Cache Stampede 방지)
        String lockKey = CACHE_LOCK_PREFIX + key;
        
        return lockManager.executeWithLock(lockKey, CACHE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> {
            // 3. 이중 체크 - 다른 스레드가 이미 캐시를 갱신했을 수 있음
            T doubleCheckValue = get(key, type);
            if (doubleCheckValue != null) {
                return doubleCheckValue;
            }

            // 4. DB에서 조회
            T loadedValue = loader.get();
            
            // 5. 캐시에 저장
            if (loadedValue != null) {
                set(key, loadedValue, ttl);
            }
            
            return loadedValue;
        });
    }

    /**
     * 캐시 갱신 (Write-Through 패턴)
     * 
     * DB 업데이트 후 캐시도 함께 갱신합니다.
     * 
     * @param key 캐시 키
     * @param value 새 값
     * @param ttl 캐시 TTL
     */
    public void update(String key, Object value, Duration ttl) {
        set(key, value, ttl);
    }

    /**
     * 캐시 무효화 (Write-Through 패턴)
     * 
     * DB 업데이트 후 관련 캐시를 삭제합니다.
     * 
     * @param key 캐시 키
     */
    public void invalidate(String key) {
        delete(key);
    }

    /**
     * 캐시 키 존재 여부 확인
     * 
     * @param key 캐시 키
     * @return 존재 여부
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 캐시 TTL 조회
     * 
     * @param key 캐시 키
     * @return 남은 TTL (초), 없으면 -1
     */
    public long getTtl(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -1;
    }

    /**
     * 캐시 통계용 - 특정 패턴의 키 개수 조회
     * 
     * @param pattern 키 패턴
     * @return 키 개수
     */
    public long countByPattern(String pattern) {
        var keys = redisTemplate.keys(pattern);
        return keys != null ? keys.size() : 0;
    }
}

