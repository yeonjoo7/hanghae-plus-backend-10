package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.application.service.StockService;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.ProductState;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 재고 관리 동시성 제어 테스트
 * 
 * 재고 동시 차감에서 Race Condition이 발생하지 않는지 검증합니다.
 */
@SpringBootTest
class StockConcurrencyTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private LockManager lockManager;

    private Product testProduct;
    private Stock testStock;

    @BeforeEach
    void setUp() {
        // 락 매니저 정리
        lockManager.clearAllLocks();

        // 테스트 상품 생성
        testProduct = Product.create(
            "테스트 상품",
            "동시성 테스트용 상품",
            com.hanghae.ecommerce.domain.product.Money.of(10000),
            Quantity.of(10) // 1인당 최대 10개까지 구매 가능
        );
        testProduct = productRepository.save(testProduct);

        // 테스트 재고 생성 (초기 재고: 1000개)
        testStock = Stock.create(testProduct.getId(), null, Quantity.of(1000));
        testStock = stockRepository.save(testStock);
    }

    @Test
    @DisplayName("재고 동시 차감 - 1000개 재고에 1000명이 각각 1개씩 동시 요청")
    void testConcurrentStockReduction() throws InterruptedException {
        // given
        int initialStock = 1000;
        int concurrentUsers = 1000;
        int quantityPerUser = 1;
        int threadPoolSize = 50;

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        // when
        for (int i = 0; i < concurrentUsers; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    
                    stockService.reduceStock(testProduct.getId(), quantityPerUser);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 모든 스레드 동시 시작
        Thread.sleep(100);
        startLatch.countDown();

        // 모든 작업 완료 대기
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();

        // 성공한 차감 건수는 초기 재고와 일치해야 함
        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(failureCount.get()).isZero();

        // 실제 재고 확인
        Stock updatedStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        assertThat(updatedStock.getAvailableQuantity().getValue()).isZero();
        assertThat(updatedStock.getSoldQuantity().getValue()).isEqualTo(initialStock);

        executorService.shutdown();

        System.out.printf("재고 차감 테스트 - 성공: %d, 실패: %d%n", 
                         successCount.get(), failureCount.get());
    }

    @Test
    @DisplayName("재고 부족 시 동시 요청 처리")
    void testConcurrentStockReductionWithInsufficientStock() throws InterruptedException {
        // given
        int initialStock = 100;
        int concurrentUsers = 200;
        int quantityPerUser = 1;

        // 재고를 100개로 설정
        Stock limitedStock = Stock.create(testProduct.getId(), null, Quantity.of(initialStock));
        stockRepository.save(limitedStock);

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < concurrentUsers; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    stockService.reduceStock(testProduct.getId(), quantityPerUser);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();

        // 성공 건수는 초기 재고와 일치
        assertThat(successCount.get()).isEqualTo(initialStock);
        // 실패 건수는 나머지와 일치
        assertThat(failureCount.get()).isEqualTo(concurrentUsers - initialStock);

        // 최종 재고는 0이어야 함
        Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        assertThat(finalStock.getAvailableQuantity().getValue()).isZero();

        executorService.shutdown();

        System.out.printf("재고 부족 테스트 - 성공: %d, 실패: %d%n", 
                         successCount.get(), failureCount.get());
    }

    @Test
    @DisplayName("대량 재고 차감 동시성 테스트")
    void testHighVolumeConcurrentStockReduction() throws InterruptedException {
        // given
        int initialStock = 10000;
        int concurrentUsers = 500;
        int quantityPerUser = 20; // 각 사용자가 20개씩 구매

        // 대량 재고 설정
        Stock highVolumeStock = Stock.create(testProduct.getId(), null, Quantity.of(initialStock));
        stockRepository.save(highVolumeStock);

        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalReducedQuantity = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // when
        for (int i = 0; i < concurrentUsers; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    stockService.reduceStock(testProduct.getId(), quantityPerUser);
                    successCount.incrementAndGet();
                    totalReducedQuantity.addAndGet(quantityPerUser);
                } catch (Exception e) {
                    // 재고 부족으로 인한 실패
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        // then
        assertThat(finished).isTrue();

        // 성능 측정
        long duration = endTime - startTime;
        double throughput = (double) concurrentUsers / duration * 1000;

        System.out.printf("대량 재고 테스트 - 처리 시간: %dms, 처리량: %.2f TPS%n", 
                         duration, throughput);

        // 총 차감된 수량이 초기 재고를 초과하지 않아야 함
        assertThat(totalReducedQuantity.get()).isLessThanOrEqualTo(initialStock);

        // 최종 재고 + 차감된 재고 = 초기 재고
        Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        int finalAvailableQuantity = finalStock.getAvailableQuantity().getValue();
        int finalSoldQuantity = finalStock.getSoldQuantity().getValue();

        assertThat(finalAvailableQuantity + finalSoldQuantity).isEqualTo(initialStock);

        executorService.shutdown();
    }

    @Test
    @DisplayName("재고 차감과 복원 동시 처리")
    void testConcurrentStockReductionAndRestoration() throws InterruptedException {
        // given
        int initialStock = 500;
        int operations = 200; // 차감 100번, 복원 100번

        Stock testStock = Stock.create(testProduct.getId(), null, Quantity.of(initialStock));
        stockRepository.save(testStock);

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(operations);

        AtomicInteger reductions = new AtomicInteger(0);
        AtomicInteger restorations = new AtomicInteger(0);

        // when
        // 차감 작업
        for (int i = 0; i < operations / 2; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    stockService.reduceStock(testProduct.getId(), 1);
                    reductions.incrementAndGet();
                } catch (Exception e) {
                    // 재고 부족으로 인한 실패
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 복원 작업
        for (int i = 0; i < operations / 2; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    stockService.restoreStock(testProduct.getId(), 1);
                    restorations.incrementAndGet();
                } catch (Exception e) {
                    // 복원 실패
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();

        Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        
        // 최종 재고 = 초기 재고 - 성공한 차감 + 성공한 복원
        int expectedFinalStock = initialStock - reductions.get() + restorations.get();
        int actualFinalStock = finalStock.getAvailableQuantity().getValue() + finalStock.getSoldQuantity().getValue();
        
        assertThat(actualFinalStock).isEqualTo(expectedFinalStock);

        executorService.shutdown();

        System.out.printf("차감/복원 테스트 - 차감: %d, 복원: %d, 최종 재고: %d%n", 
                         reductions.get(), restorations.get(), 
                         finalStock.getAvailableQuantity().getValue());
    }

    @Test
    @DisplayName("여러 상품 동시 재고 관리")
    void testMultipleProductsConcurrentStockManagement() throws InterruptedException {
        // given
        List<Product> products = new ArrayList<>();
        List<Stock> stocks = new ArrayList<>();

        // 5개 상품 생성
        for (int i = 1; i <= 5; i++) {
            Product product = Product.create(
                "상품" + i,
                "테스트 상품" + i,
                com.hanghae.ecommerce.domain.product.Money.of(1000 * i),
                Quantity.of(5)
            );
            product = productRepository.save(product);
            products.add(product);

            Stock stock = Stock.create(product.getId(), null, Quantity.of(100));
            stock = stockRepository.save(stock);
            stocks.add(stock);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(250); // 각 상품당 50개 요청

        AtomicInteger totalSuccess = new AtomicInteger(0);

        // when
        for (Product product : products) {
            for (int i = 0; i < 50; i++) {
                executorService.execute(() -> {
                    try {
                        startLatch.await();
                        stockService.reduceStock(product.getId(), 1);
                        totalSuccess.incrementAndGet();
                    } catch (Exception e) {
                        // 실패 (각 상품의 재고가 100개이므로 50개 요청은 모두 성공해야 함)
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();
        assertThat(totalSuccess.get()).isEqualTo(250); // 모든 요청이 성공해야 함

        // 각 상품의 재고 확인
        for (Product product : products) {
            Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(product.getId())
                    .orElseThrow();
            assertThat(finalStock.getAvailableQuantity().getValue()).isEqualTo(50);
            assertThat(finalStock.getSoldQuantity().getValue()).isEqualTo(50);
        }

        executorService.shutdown();
    }

    @Test
    @DisplayName("재고 차감 성능 벤치마크")
    void testStockReductionPerformanceBenchmark() throws InterruptedException {
        // given
        int operations = 1000;
        int threadPoolSize = 100;

        Stock performanceStock = Stock.create(testProduct.getId(), null, Quantity.of(operations));
        stockRepository.save(performanceStock);

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(operations);

        long startTime = System.nanoTime();

        // when
        for (int i = 0; i < operations; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    stockService.reduceStock(testProduct.getId(), 1);
                } catch (Exception e) {
                    // 무시
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);
        long endTime = System.nanoTime();

        // then
        assertThat(finished).isTrue();

        long durationMs = (endTime - startTime) / 1_000_000;
        double throughput = (double) operations / durationMs * 1000;

        System.out.printf("재고 차감 성능 - %d건 처리 시간: %dms, 처리량: %.2f TPS%n", 
                         operations, durationMs, throughput);

        // 성능 기준: 1000건을 10초 내에 처리
        assertThat(durationMs).isLessThan(10000);

        executorService.shutdown();
    }
}