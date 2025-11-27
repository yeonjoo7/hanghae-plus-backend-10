package com.hanghae.ecommerce.cache;

import com.hanghae.ecommerce.infrastructure.cache.RedisCacheService;
import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.Serializable;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 캐시 서비스 테스트
 * 
 * Cache Stampede 방지 기능을 집중적으로 검증합니다.
 */
@DisplayName("RedisCacheService 테스트")
class RedisCacheServiceTest extends BaseIntegrationTest {

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TEST_CACHE_PREFIX = "test-cache:";

    @BeforeEach
    void setUp() {
        // 테스트 캐시 초기화
        Set<String> keys = redisTemplate.keys(TEST_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("기본 캐시 동작 - 저장 및 조회")
    void testBasicCacheOperations() {
        // given
        String key = TEST_CACHE_PREFIX + "basic";
        TestCacheData data = new TestCacheData("테스트", 123);

        // when
        redisCacheService.set(key, data, Duration.ofMinutes(5));

        // then
        assertThat(redisCacheService.exists(key)).isTrue();
        
        TestCacheData retrieved = redisCacheService.get(key, TestCacheData.class);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("테스트");
        assertThat(retrieved.getValue()).isEqualTo(123);
    }

    @Test
    @DisplayName("캐시 삭제 테스트")
    void testCacheDelete() {
        // given
        String key = TEST_CACHE_PREFIX + "delete";
        redisCacheService.set(key, new TestCacheData("삭제테스트", 1), Duration.ofMinutes(5));
        assertThat(redisCacheService.exists(key)).isTrue();

        // when
        redisCacheService.delete(key);

        // then
        assertThat(redisCacheService.exists(key)).isFalse();
    }

    @Test
    @DisplayName("패턴으로 캐시 삭제")
    void testDeleteByPattern() {
        // given
        redisCacheService.set(TEST_CACHE_PREFIX + "pattern1", new TestCacheData("1", 1), Duration.ofMinutes(5));
        redisCacheService.set(TEST_CACHE_PREFIX + "pattern2", new TestCacheData("2", 2), Duration.ofMinutes(5));
        redisCacheService.set(TEST_CACHE_PREFIX + "pattern3", new TestCacheData("3", 3), Duration.ofMinutes(5));

        // when
        redisCacheService.deleteByPattern(TEST_CACHE_PREFIX + "*");

        // then
        assertThat(redisCacheService.exists(TEST_CACHE_PREFIX + "pattern1")).isFalse();
        assertThat(redisCacheService.exists(TEST_CACHE_PREFIX + "pattern2")).isFalse();
        assertThat(redisCacheService.exists(TEST_CACHE_PREFIX + "pattern3")).isFalse();
    }

    @Test
    @DisplayName("getOrLoad - 캐시 미스 시 로더 호출")
    void testGetOrLoadCacheMiss() {
        // given
        String key = TEST_CACHE_PREFIX + "loader";
        AtomicInteger loaderCallCount = new AtomicInteger(0);

        // when
        TestCacheData result = redisCacheService.getOrLoad(
            key,
            TestCacheData.class,
            Duration.ofMinutes(5),
            () -> {
                loaderCallCount.incrementAndGet();
                return new TestCacheData("로더결과", 999);
            }
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("로더결과");
        assertThat(loaderCallCount.get()).isEqualTo(1);
        assertThat(redisCacheService.exists(key)).isTrue();
    }

    @Test
    @DisplayName("getOrLoad - 캐시 히트 시 로더 미호출")
    void testGetOrLoadCacheHit() {
        // given
        String key = TEST_CACHE_PREFIX + "hit";
        redisCacheService.set(key, new TestCacheData("기존데이터", 100), Duration.ofMinutes(5));
        AtomicInteger loaderCallCount = new AtomicInteger(0);

        // when
        TestCacheData result = redisCacheService.getOrLoad(
            key,
            TestCacheData.class,
            Duration.ofMinutes(5),
            () -> {
                loaderCallCount.incrementAndGet();
                return new TestCacheData("새데이터", 200);
            }
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("기존데이터");
        assertThat(result.getValue()).isEqualTo(100);
        assertThat(loaderCallCount.get())
            .as("캐시 히트 시 로더가 호출되지 않아야 함")
            .isZero();
    }

    @Test
    @DisplayName("Cache Stampede 방지 - 동시 요청 시 로더가 1번만 호출됨")
    void testCacheStampedePrevention() throws InterruptedException {
        // given
        String key = TEST_CACHE_PREFIX + "stampede";
        int concurrentRequests = 50;
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);
        
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // when: 50개 요청이 동시에 시작
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    TestCacheData result = redisCacheService.getOrLoad(
                        key,
                        TestCacheData.class,
                        Duration.ofMinutes(5),
                        () -> {
                            int callNumber = loaderCallCount.incrementAndGet();
                            System.out.println("로더 호출 #" + callNumber + " (Thread: " + Thread.currentThread().getId() + ")");
                            
                            // 로더 실행에 시간이 걸린다고 가정
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            
                            return new TestCacheData("결과", callNumber);
                        }
                    );
                    
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("요청 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        startLatch.countDown();
        
        // 완료 대기
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(concurrentRequests);
        
        // 핵심: 로더는 정확히 1번만 호출되어야 함!
        assertThat(loaderCallCount.get())
            .as("Cache Stampede 방지: 로더는 1번만 호출되어야 함")
            .isEqualTo(1);

        System.out.println("=== Cache Stampede 방지 테스트 결과 ===");
        System.out.printf("동시 요청 수: %d%n", concurrentRequests);
        System.out.printf("로더 호출 횟수: %d (기대값: 1)%n", loaderCallCount.get());
        System.out.printf("성공한 요청: %d%n", successCount.get());
    }

    @Test
    @DisplayName("Cache Stampede 방지 - Double-Check 동작 확인")
    void testDoubleCheckLocking() throws InterruptedException {
        // given
        String key = TEST_CACHE_PREFIX + "doublecheck";
        int concurrentRequests = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);
        
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger consistentResults = new AtomicInteger(0);

        // when
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    TestCacheData result = redisCacheService.getOrLoad(
                        key,
                        TestCacheData.class,
                        Duration.ofMinutes(5),
                        () -> {
                            int callNumber = loaderCallCount.incrementAndGet();
                            // 비용이 큰 작업 시뮬레이션
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return new TestCacheData("결과", 42); // 항상 동일한 값
                        }
                    );
                    
                    if (result != null) {
                        successCount.incrementAndGet();
                        if (result.getValue() == 42) {
                            consistentResults.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("요청 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(concurrentRequests);
        assertThat(consistentResults.get())
            .as("모든 요청이 동일한 결과를 받아야 함")
            .isEqualTo(concurrentRequests);
        assertThat(loaderCallCount.get())
            .as("로더는 1번만 호출되어야 함")
            .isEqualTo(1);

        System.out.println("=== Double-Check Locking 테스트 결과 ===");
        System.out.printf("동시 요청 수: %d%n", concurrentRequests);
        System.out.printf("로더 호출 횟수: %d%n", loaderCallCount.get());
        System.out.printf("일관된 결과 수: %d%n", consistentResults.get());
    }

    @Test
    @DisplayName("TTL 설정 확인")
    void testTtl() {
        // given
        String key = TEST_CACHE_PREFIX + "ttl";
        Duration ttl = Duration.ofMinutes(5);

        // when
        redisCacheService.set(key, new TestCacheData("TTL테스트", 1), ttl);

        // then
        long remainingTtl = redisCacheService.getTtl(key);
        assertThat(remainingTtl).isGreaterThan(0);
        assertThat(remainingTtl).isLessThanOrEqualTo(300); // 5분 = 300초

        System.out.printf("설정된 TTL: %d초%n", remainingTtl);
    }

    /**
     * 테스트용 캐시 데이터 클래스
     */
    public static class TestCacheData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String name;
        private int value;

        public TestCacheData() {}

        public TestCacheData(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
}

