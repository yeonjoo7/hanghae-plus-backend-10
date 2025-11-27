package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.application.product.StockService;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.ProductState;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 재고 관리 동시성 제어 테스트
 * 분산 락을 사용한 재고 차감/복구의 동시성 제어를 검증합니다.
 */
@DisplayName("재고 동시성 테스트")
class StockConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private LockManager lockManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Product testProduct;
    private Stock testStock;

    /**
     * 별도 트랜잭션으로 재고 설정 (즉시 커밋)
     */
    private void setupStockInNewTransaction(Long productId, int quantity) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        transactionTemplate.execute(status -> {
            stockRepository.deleteByProductId(productId);
            Stock stock = Stock.createForProduct(productId, Quantity.of(quantity), null);
            stockRepository.save(stock);
            return null;
        });
    }

    @BeforeEach
    void setUp() {
        // 락 매니저 정리
        lockManager.clearAllLocks();

        // 테스트 상품 생성
        testProduct = Product.create(
                "테스트 상품 " + System.currentTimeMillis(),
                "동시성 테스트용 상품",
                com.hanghae.ecommerce.domain.product.Money.of(10000),
                Quantity.of(10));
        testProduct = productRepository.save(testProduct);

        // 테스트 재고 생성 (초기 재고: 1000개)
        testStock = Stock.createForProduct(testProduct.getId(), Quantity.of(1000), null);
        testStock = stockRepository.save(testStock);
    }

    @Test
    @DisplayName("재고 동시 차감 - 1000개 재고에 1000명이 각각 1개씩 동시 요청")
    @DirtiesContext
    void testConcurrentStockReduction() throws InterruptedException {
        // given
        int initialStock = 1000;
        int concurrentUsers = 1000;
        int quantityPerUser = 1;

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

        // 모든 스레드 동시 시작
        startLatch.countDown();

        // 모든 작업 완료 대기
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(failureCount.get()).isZero();

        // JPA를 사용한 DB 검증
        Stock updatedStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        assertThat(updatedStock.getAvailableQuantity().getValue()).isZero();
        assertThat(updatedStock.getSoldQuantity().getValue()).isEqualTo(initialStock);

        // 상품 상태 확인 (재고 소진 시 품절 처리)
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getState()).isEqualTo(ProductState.OUT_OF_STOCK);

        executorService.shutdown();

        System.out.printf("재고 차감 테스트 - 성공: %d, 실패: %d%n",
                successCount.get(), failureCount.get());
    }

    @Test
    @DisplayName("재고 부족 시 동시 요청 처리")
    @DirtiesContext
    void testConcurrentStockReductionWithInsufficientStock() throws InterruptedException {
        // given
        int initialStock = 100;
        int concurrentUsers = 200;
        int quantityPerUser = 1;

        setupStockInNewTransaction(testProduct.getId(), initialStock);

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
        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(failureCount.get()).isEqualTo(concurrentUsers - initialStock);

        // JPA를 사용한 DB 검증
        Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        assertThat(finalStock.getAvailableQuantity().getValue()).isZero();
        assertThat(finalStock.getSoldQuantity().getValue()).isEqualTo(initialStock);

        executorService.shutdown();

        System.out.printf("재고 부족 테스트 - 성공: %d, 실패: %d%n",
                successCount.get(), failureCount.get());
    }

    @Test
    @DisplayName("재고 차감과 복원 동시 처리")
    @DirtiesContext
    void testConcurrentStockReductionAndRestoration() throws InterruptedException {
        // given
        int initialStock = 500;
        int operations = 200;

        setupStockInNewTransaction(testProduct.getId(), initialStock);

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(operations);

        AtomicInteger reductions = new AtomicInteger(0);
        AtomicInteger restorations = new AtomicInteger(0);

        // when: 차감 100번, 복원 100번
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

        // JPA를 사용한 DB 검증
        Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();

        // 최종 재고 = 초기 재고 - 성공한 차감 + 성공한 복원
        int expectedAvailable = initialStock - reductions.get() + restorations.get();
        assertThat(finalStock.getAvailableQuantity().getValue()).isEqualTo(expectedAvailable);

        executorService.shutdown();

        System.out.printf("차감/복원 테스트 - 차감: %d, 복원: %d, 최종 재고: %d%n",
                reductions.get(), restorations.get(),
                finalStock.getAvailableQuantity().getValue());
    }

    @Test
    @DisplayName("분산 락 동작 검증 - 동시 요청 시 순차적으로 처리됨")
    @DirtiesContext
    void testDistributedLockBehavior() throws InterruptedException {
        // given
        int initialStock = 50;
        int concurrentUsers = 100;

        setupStockInNewTransaction(testProduct.getId(), initialStock);

        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentHashMap<Long, Long> threadTimestamps = new ConcurrentHashMap<>();

        // when: 100명이 동시에 요청
        for (int i = 0; i < concurrentUsers; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    long startTime = System.nanoTime();
                    stockService.reduceStock(testProduct.getId(), 1);
                    long endTime = System.nanoTime();

                    threadTimestamps.put(Thread.currentThread().getId(), endTime - startTime);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패는 정상
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 정확히 50명만 성공
        assertThat(successCount.get()).isEqualTo(initialStock);

        // 분산 락이 제대로 동작했는지 검증
        Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        assertThat(finalStock.getAvailableQuantity().getValue()).isZero();
        assertThat(finalStock.getSoldQuantity().getValue()).isEqualTo(initialStock);

        // 상품이 품절 상태로 변경되었는지 확인
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getState()).isEqualTo(ProductState.OUT_OF_STOCK);

        System.out.println("=== Distributed Lock Test Results ===");
        System.out.println("Success count: " + successCount.get());
        System.out.println("Average processing time: " +
                threadTimestamps.values().stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0) / 1_000_000
                + "ms");
    }

    @Test
    @DisplayName("재고 소진 후 추가 요청 - 모두 실패")
    @DirtiesContext
    void testStockReductionAfterSoldOut() throws InterruptedException {
        // given: 10개 재고
        int initialStock = 10;
        setupStockInNewTransaction(testProduct.getId(), initialStock);

        // when: 먼저 10개를 모두 소진
        for (int i = 0; i < initialStock; i++) {
            stockService.reduceStock(testProduct.getId(), 1);
        }

        // then: 재고가 모두 소진됨
        Stock soldOutStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        assertThat(soldOutStock.getAvailableQuantity().getValue()).isZero();

        // when: 추가로 20명이 동시에 요청
        int additionalUsers = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(additionalUsers);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < additionalUsers; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    stockService.reduceStock(testProduct.getId(), 1);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 모든 요청이 실패
        assertThat(failureCount.get()).isEqualTo(additionalUsers);

        // 여전히 재고는 0
        Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        assertThat(finalStock.getAvailableQuantity().getValue()).isZero();
        assertThat(finalStock.getSoldQuantity().getValue()).isEqualTo(initialStock);
    }

    @Test
    @DisplayName("재고 복원 후 상품 상태 변경 검증")
    @DirtiesContext
    void testProductStateChangeAfterStockRestoration() throws InterruptedException {
        // given: 10개 재고를 모두 소진
        int initialStock = 10;
        setupStockInNewTransaction(testProduct.getId(), initialStock);

        for (int i = 0; i < initialStock; i++) {
            stockService.reduceStock(testProduct.getId(), 1);
        }

        // 품절 상태 확인
        Product soldOutProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(soldOutProduct.getState()).isEqualTo(ProductState.OUT_OF_STOCK);

        // when: 재고 복원
        stockService.restoreStock(testProduct.getId(), 5);

        // then: 상품이 다시 판매 가능 상태로 변경
        Product restoredProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(restoredProduct.getState()).isEqualTo(ProductState.NORMAL);

        Stock restoredStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        assertThat(restoredStock.getAvailableQuantity().getValue()).isEqualTo(5);
    }

    @Test
    @DisplayName("여러 상품 동시 재고 관리")
    @DirtiesContext
    void testMultipleProductsConcurrentStockManagement() throws InterruptedException {
        // given: 5개 상품 생성
        List<Product> products = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        for (int i = 1; i <= 5; i++) {
            Product product = Product.create(
                    "상품" + timestamp + "_" + i,
                    "테스트 상품" + i,
                    com.hanghae.ecommerce.domain.product.Money.of(1000 * i),
                    Quantity.of(5));
            product = productRepository.save(product);
            products.add(product);

            Stock stock = Stock.createForProduct(product.getId(), Quantity.of(100), null);
            stockRepository.save(stock);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(250);

        AtomicInteger totalSuccess = new AtomicInteger(0);

        // when: 각 상품당 50개 요청
        for (Product product : products) {
            for (int i = 0; i < 50; i++) {
                executorService.execute(() -> {
                    try {
                        startLatch.await();
                        stockService.reduceStock(product.getId(), 1);
                        totalSuccess.incrementAndGet();
                    } catch (Exception e) {
                        // 실패
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
        assertThat(totalSuccess.get()).isEqualTo(250);

        // JPA를 사용한 각 상품의 재고 확인
        for (Product product : products) {
            Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(product.getId())
                    .orElseThrow();
            assertThat(finalStock.getAvailableQuantity().getValue()).isEqualTo(50);
            assertThat(finalStock.getSoldQuantity().getValue()).isEqualTo(50);
        }

        executorService.shutdown();
    }
}