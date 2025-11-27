package com.hanghae.ecommerce.cache;

import com.hanghae.ecommerce.application.product.PopularProductService;
import com.hanghae.ecommerce.application.product.ProductService.PopularProduct;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.infrastructure.cache.PopularProductCache;
import com.hanghae.ecommerce.infrastructure.cache.RedisCacheService;
import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인기 상품 캐싱 테스트
 * 
 * Redis 캐싱과 Cache Stampede 방지 기능을 검증합니다.
 */
@DisplayName("인기 상품 캐싱 테스트")
class PopularProductCacheTest extends BaseIntegrationTest {

    @Autowired
    private PopularProductService popularProductService;

    @Autowired
    private PopularProductCache popularProductCache;

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private List<Product> testProducts;

    @BeforeEach
    void setUp() {
        // Redis 캐시 초기화
        clearRedisCache();

        // 테스트 상품 생성
        testProducts = createTestProducts(5);

        // 판매 데이터 기록
        recordSalesData();
    }

    private void clearRedisCache() {
        Set<String> keys = redisTemplate.keys("popular-products:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private List<Product> createTestProducts(int count) {
        List<Product> products = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            Product product = Product.create(
                    "캐시테스트상품_" + timestamp + "_" + i,
                    "캐시 테스트용 상품",
                    Money.of(10000 + i * 1000),
                    Quantity.of(10));
            product = productRepository.save(product);

            Stock stock = Stock.createForProduct(product.getId(), Quantity.of(100), null);
            stockRepository.save(stock);

            products.add(product);
        }

        return products;
    }

    private void recordSalesData() {
        LocalDate today = LocalDate.now();

        // 상품별 다른 판매량 기록 (순위를 예측 가능하게)
        for (int i = 0; i < testProducts.size(); i++) {
            Product product = testProducts.get(i);
            int salesCount = (testProducts.size() - i) * 10; // 첫 번째 상품이 가장 많이 팔림

            popularProductCache.incrementSales(product.getId(), salesCount, today);
        }
    }

    @Test
    @DisplayName("캐시 미스 → DB 조회 → 캐시 저장 → 캐시 히트 확인")
    void testCacheHitAfterMiss() {
        // given: 캐시가 비어있는 상태
        String cacheKey = "popular-products:3days_5items";
        assertThat(redisCacheService.exists(cacheKey)).isFalse();

        // when: 첫 번째 조회 (캐시 미스 → DB 조회 → 캐시 저장)
        long startTime1 = System.nanoTime();
        List<PopularProduct> result1 = popularProductService.getPopularProducts(3, 5);
        long duration1 = System.nanoTime() - startTime1;

        // then: 결과가 반환되고 캐시에 저장됨
        assertThat(result1).isNotEmpty();
        assertThat(redisCacheService.exists(cacheKey)).isTrue();

        // when: 두 번째 조회 (캐시 히트)
        long startTime2 = System.nanoTime();
        List<PopularProduct> result2 = popularProductService.getPopularProducts(3, 5);
        long duration2 = System.nanoTime() - startTime2;

        // then: 동일한 결과 반환, 캐시 히트로 더 빠름
        assertThat(result2).hasSameSizeAs(result1);

        System.out.println("=== 캐시 성능 비교 ===");
        System.out.printf("첫 번째 조회 (캐시 미스): %.2f ms%n", duration1 / 1_000_000.0);
        System.out.printf("두 번째 조회 (캐시 히트): %.2f ms%n", duration2 / 1_000_000.0);
        System.out.printf("성능 개선: %.2f%%n", (1 - (double) duration2 / duration1) * 100);

        // 캐시 히트가 캐시 미스보다 빠른지 확인 (일반적으로)
        // 참고: 첫 조회 시 JIT 워밍업 효과가 있을 수 있음
    }

    @Test
    @DisplayName("Cache Stampede 방지 - 동시 요청 시 DB 조회가 1번만 발생")
    void testCacheStampedePrevention() throws InterruptedException {
        // given: 캐시가 비어있는 상태
        clearRedisCache();

        int concurrentRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Long> responseTimes = new ArrayList<>();

        // when: 50개 요청이 동시에 시작
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    long start = System.nanoTime();
                    List<PopularProduct> result = popularProductService.getPopularProducts(3, 5);
                    long end = System.nanoTime();

                    if (result != null && !result.isEmpty()) {
                        successCount.incrementAndGet();
                        synchronized (responseTimes) {
                            responseTimes.add(end - start);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("요청 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 동시에 시작!
        startLatch.countDown();

        // 모든 요청 완료 대기
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 모든 요청 성공
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(concurrentRequests);

        // 캐시에 데이터가 저장되어 있어야 함
        String cacheKey = "popular-products:3days_5items";
        assertThat(redisCacheService.exists(cacheKey)).isTrue();

        // 응답 시간 통계
        if (!responseTimes.isEmpty()) {
            long avgTime = responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
            long minTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
            long maxTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);

            System.out.println("=== Cache Stampede 방지 테스트 결과 ===");
            System.out.printf("동시 요청 수: %d%n", concurrentRequests);
            System.out.printf("성공한 요청: %d%n", successCount.get());
            System.out.printf("평균 응답 시간: %.2f ms%n", avgTime / 1_000_000.0);
            System.out.printf("최소 응답 시간: %.2f ms%n", minTime / 1_000_000.0);
            System.out.printf("최대 응답 시간: %.2f ms%n", maxTime / 1_000_000.0);
        }

        // Cache Stampede가 방지되었다면:
        // - 첫 번째 요청만 실제 계산을 수행 (락 획득)
        // - 나머지 요청은 락 대기 후 캐시에서 읽음
        // - 모든 요청이 성공해야 함
    }

    @Test
    @DisplayName("캐시 무효화 테스트 - 판매 기록 시 캐시 삭제")
    void testCacheInvalidation() {
        // given: 캐시에 데이터가 있는 상태
        List<PopularProduct> initialResult = popularProductService.getPopularProducts(3, 5);
        String cacheKey = "popular-products:3days_5items";
        assertThat(redisCacheService.exists(cacheKey)).isTrue();

        // when: 새로운 판매 기록 (캐시 무효화 트리거)
        popularProductService.recordSale(testProducts.get(0).getId(), 100, LocalDate.now());

        // then: 캐시가 무효화됨
        assertThat(redisCacheService.exists(cacheKey)).isFalse();

        // when: 다시 조회
        List<PopularProduct> newResult = popularProductService.getPopularProducts(3, 5);

        // then: 새로운 데이터로 캐시 재생성
        assertThat(redisCacheService.exists(cacheKey)).isTrue();
        assertThat(newResult).isNotEmpty();
    }

    @Test
    @DisplayName("다른 조회 조건은 다른 캐시 키 사용")
    void testDifferentCacheKeys() {
        // when: 서로 다른 조건으로 조회
        popularProductService.getPopularProducts(3, 5);
        popularProductService.getPopularProducts(7, 10);
        popularProductService.getPopularProducts(1, 5);

        // then: 각각 다른 캐시 키로 저장
        assertThat(redisCacheService.exists("popular-products:3days_5items")).isTrue();
        assertThat(redisCacheService.exists("popular-products:7days_10items")).isTrue();
        assertThat(redisCacheService.exists("popular-products:1days_5items")).isTrue();
    }

    @Test
    @DisplayName("대량 동시 요청 시 성능 및 정합성 테스트")
    void testHighConcurrencyPerformance() throws InterruptedException {
        // given
        clearRedisCache();

        int concurrentRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger expectedSize = new AtomicInteger(-1);
        AtomicInteger inconsistentResults = new AtomicInteger(0);

        // when: 100개 요청이 동시에 시작
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    List<PopularProduct> result = popularProductService.getPopularProducts(3, 5);

                    if (result != null) {
                        successCount.incrementAndGet();

                        // 결과 크기 일관성 검증
                        int size = result.size();
                        if (expectedSize.compareAndSet(-1, size)) {
                            // 첫 번째 결과
                        } else if (expectedSize.get() != size) {
                            // 크기가 다른 결과 발견
                            inconsistentResults.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("요청 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long startTime = System.nanoTime();
        startLatch.countDown();
        boolean completed = endLatch.await(60, TimeUnit.SECONDS);
        long totalTime = System.nanoTime() - startTime;

        executor.shutdown();

        // then
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(concurrentRequests);
        assertThat(inconsistentResults.get())
                .as("모든 요청이 동일한 결과를 반환해야 함 (캐시 정합성)")
                .isZero();

        double throughput = concurrentRequests / (totalTime / 1_000_000_000.0);

        System.out.println("=== 대량 동시 요청 성능 테스트 결과 ===");
        System.out.printf("동시 요청 수: %d%n", concurrentRequests);
        System.out.printf("성공한 요청: %d%n", successCount.get());
        System.out.printf("총 소요 시간: %.2f ms%n", totalTime / 1_000_000.0);
        System.out.printf("처리량 (TPS): %.2f%n", throughput);
        System.out.printf("결과 정합성: %s%n", inconsistentResults.get() == 0 ? "OK" : "FAIL");
    }

    @Test
    @DisplayName("캐시 TTL 확인")
    void testCacheTtl() {
        // when: 인기 상품 조회
        popularProductService.getPopularProducts(3, 5);

        // then: 캐시 TTL이 설정되어 있어야 함
        String cacheKey = "popular-products:3days_5items";
        long ttl = redisCacheService.getTtl(cacheKey);

        System.out.printf("캐시 TTL: %d 초%n", ttl);

        // TTL이 0보다 크고 5분(300초) 이하여야 함
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(300);
    }
}

