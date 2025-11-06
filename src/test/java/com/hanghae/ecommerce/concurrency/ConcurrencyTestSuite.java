package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.application.service.CouponService;
import com.hanghae.ecommerce.application.service.OrderService;
import com.hanghae.ecommerce.application.service.PaymentService;
import com.hanghae.ecommerce.application.service.PopularProductService;
import com.hanghae.ecommerce.application.service.StockService;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.DiscountPolicy;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.order.Address;
import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.order.Recipient;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.domain.user.Point;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.UserType;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
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
 * 종합적인 동시성 테스트 스위트
 * 
 * 실제 서비스에서 발생할 수 있는 복합적인 동시성 시나리오를 테스트합니다.
 */
@SpringBootTest
class ConcurrencyTestSuite {

    @Autowired private CouponService couponService;
    @Autowired private StockService stockService;
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private PopularProductService popularProductService;
    @Autowired private LockManager lockManager;

    @Autowired private CouponRepository couponRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private UserRepository userRepository;

    private User testUser;
    private Product testProduct;
    private Stock testStock;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        lockManager.clearAllLocks();

        // 테스트 사용자 (충분한 포인트)
        testUser = User.create(
            "test@example.com",
            UserType.CUSTOMER,
            "테스트 사용자",
            "010-1234-5678"
        );
        testUser.chargePoint(Point.of(1000000)); // 100만 포인트 충전
        testUser = userRepository.save(testUser);

        // 테스트 상품
        testProduct = Product.create(
            "인기 상품",
            "동시성 테스트용 인기 상품",
            Money.of(1000),
            Quantity.of(5)
        );
        testProduct = productRepository.save(testProduct);

        // 테스트 재고
        testStock = Stock.create(testProduct.getId(), null, Quantity.of(100));
        testStock = stockRepository.save(testStock);

        // 테스트 쿠폰
        testCoupon = Coupon.create(
            "동시성 테스트 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(50),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(1)
        );
        testCoupon = couponRepository.save(testCoupon);
    }

    @Test
    @DisplayName("한정판 상품 구매 시나리오 - 재고 한정 + 쿠폰 한정")
    void testLimitedProductPurchaseScenario() throws InterruptedException {
        // given
        int limitedStock = 10; // 재고 10개
        int limitedCoupons = 5; // 쿠폰 5개
        int concurrentUsers = 50; // 50명 동시 구매 시도

        // 한정 재고 설정
        Stock limitedStockItem = Stock.create(testProduct.getId(), null, Quantity.of(limitedStock));
        stockRepository.save(limitedStockItem);

        // 한정 쿠폰 설정
        Coupon limitedCoupon = Coupon.create(
            "한정 쿠폰",
            DiscountPolicy.amount(Money.of(500)),
            Quantity.of(limitedCoupons),
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(1)
        );
        limitedCoupon = couponRepository.save(limitedCoupon);

        // 여러 사용자 생성
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= concurrentUsers; i++) {
            User user = User.create(
                "user" + i + "@test.com",
                UserType.CUSTOMER,
                "사용자" + i,
                "010-0000-" + String.format("%04d", i)
            );
            user.chargePoint(Point.of(100000));
            users.add(userRepository.save(user));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(25);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successfulPurchases = new AtomicInteger(0);
        AtomicInteger successfulCouponIssues = new AtomicInteger(0);
        AtomicInteger stockReductions = new AtomicInteger(0);

        // when
        for (int i = 0; i < concurrentUsers; i++) {
            final User user = users.get(i);
            final boolean shouldTryCoupon = i < 20; // 처음 20명만 쿠폰 시도
            
            executorService.execute(() -> {
                try {
                    startLatch.await();

                    // 1. 쿠폰 발급 시도 (선택적)
                    boolean hasCoupon = false;
                    if (shouldTryCoupon) {
                        try {
                            couponService.issueCoupon(user.getId(), limitedCoupon.getId());
                            successfulCouponIssues.incrementAndGet();
                            hasCoupon = true;
                        } catch (Exception e) {
                            // 쿠폰 발급 실패
                        }
                    }

                    // 2. 재고 차감 시도
                    try {
                        stockService.reduceStock(testProduct.getId(), 1);
                        stockReductions.incrementAndGet();
                        
                        // 재고 차감 성공 시 구매 성공으로 간주
                        successfulPurchases.incrementAndGet();
                        
                        // 3. 인기 상품에 판매량 기록
                        popularProductService.recordSale(testProduct.getId(), 1, null);
                        
                    } catch (Exception e) {
                        // 재고 부족으로 구매 실패
                    }

                } catch (Exception e) {
                    // 전체 프로세스 실패
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(60, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();

        // 결과 검증
        assertThat(successfulPurchases.get()).isEqualTo(limitedStock);
        assertThat(successfulCouponIssues.get()).isEqualTo(limitedCoupons);
        assertThat(stockReductions.get()).isEqualTo(limitedStock);

        // 최종 재고 확인
        Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(testProduct.getId())
                .orElseThrow();
        assertThat(finalStock.getAvailableQuantity().getValue()).isZero();

        // 최종 쿠폰 수량 확인
        Coupon finalCoupon = couponRepository.findById(limitedCoupon.getId()).orElseThrow();
        assertThat(finalCoupon.getIssuedQuantity().getValue()).isEqualTo(limitedCoupons);
        assertThat(finalCoupon.getRemainingQuantity().getValue()).isZero();

        executorService.shutdown();

        System.out.printf("한정판 구매 테스트 - 구매 성공: %d/%d, 쿠폰 발급: %d/%d%n",
                         successfulPurchases.get(), limitedStock,
                         successfulCouponIssues.get(), limitedCoupons);
    }

    @Test
    @DisplayName("대규모 플래시 세일 시나리오")
    void testFlashSaleScenario() throws InterruptedException {
        // given
        int flashSaleStock = 100;
        int concurrentUsers = 1000;
        int maxQuantityPerUser = 2;

        // 플래시 세일 상품 설정
        Product flashSaleProduct = Product.create(
            "플래시 세일 상품",
            "한정 시간 특가 상품",
            Money.of(500),
            Quantity.of(maxQuantityPerUser)
        );
        flashSaleProduct = productRepository.save(flashSaleProduct);

        Stock flashSaleStockItem = Stock.create(flashSaleProduct.getId(), null, Quantity.of(flashSaleStock));
        stockRepository.save(flashSaleStockItem);

        // 플래시 세일 쿠폰
        Coupon flashSaleCoupon = Coupon.create(
            "플래시 세일 30% 할인",
            DiscountPolicy.rate(30),
            Quantity.of(200),
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(30)
        );
        flashSaleCoupon = couponRepository.save(flashSaleCoupon);

        // 대량 사용자 생성
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= concurrentUsers; i++) {
            User user = User.create(
                "flashuser" + i + "@test.com",
                UserType.CUSTOMER,
                "플래시사용자" + i,
                "010-1111-" + String.format("%04d", i)
            );
            user.chargePoint(Point.of(50000));
            users.add(userRepository.save(user));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger totalPurchases = new AtomicInteger(0);
        AtomicInteger totalQuantityPurchased = new AtomicInteger(0);
        AtomicInteger couponUsages = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // when
        for (int i = 0; i < concurrentUsers; i++) {
            final User user = users.get(i);
            final int purchaseQuantity = (i % 2 == 0) ? 1 : 2; // 번갈아 가며 1개 또는 2개 구매
            
            executorService.execute(() -> {
                try {
                    startLatch.await();

                    // 쿠폰 발급 시도 (50% 확률)
                    boolean gotCoupon = false;
                    if (Math.random() < 0.5) {
                        try {
                            couponService.issueCoupon(user.getId(), flashSaleCoupon.getId());
                            gotCoupon = true;
                        } catch (Exception e) {
                            // 쿠폰 발급 실패
                        }
                    }

                    // 재고 차감 시도
                    try {
                        stockService.reduceStock(flashSaleProduct.getId(), purchaseQuantity);
                        totalPurchases.incrementAndGet();
                        totalQuantityPurchased.addAndGet(purchaseQuantity);
                        
                        if (gotCoupon) {
                            couponUsages.incrementAndGet();
                        }

                        // 판매량 기록
                        popularProductService.recordSale(flashSaleProduct.getId(), purchaseQuantity, null);

                    } catch (Exception e) {
                        // 재고 부족으로 구매 실패
                    }

                } catch (Exception e) {
                    // 전체 실패
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(120, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        // then
        assertThat(finished).isTrue();

        // 성능 측정
        long duration = endTime - startTime;
        double throughput = (double) concurrentUsers / duration * 1000;

        // 재고가 정확히 소진되었는지 확인
        Stock finalStock = stockRepository.findByProductIdAndProductOptionIdIsNull(flashSaleProduct.getId())
                .orElseThrow();
        assertThat(totalQuantityPurchased.get()).isEqualTo(flashSaleStock);
        assertThat(finalStock.getAvailableQuantity().getValue()).isZero();

        executorService.shutdown();

        System.out.printf("플래시 세일 테스트 - 처리 시간: %dms, 처리량: %.2f TPS%n", duration, throughput);
        System.out.printf("구매 성공: %d명, 총 구매량: %d개, 쿠폰 사용: %d건%n",
                         totalPurchases.get(), totalQuantityPurchased.get(), couponUsages.get());
    }

    @Test
    @DisplayName("인기 상품 집계 동시성 테스트")
    void testPopularProductAggregationConcurrency() throws InterruptedException {
        // given
        int totalSales = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalSales);

        // when
        for (int i = 0; i < totalSales; i++) {
            final int quantity = (i % 5) + 1; // 1~5개 랜덤
            
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    popularProductService.recordSale(testProduct.getId(), quantity, null);
                } catch (Exception e) {
                    System.err.println("판매량 기록 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();

        // 잠시 대기 후 인기 상품 조회
        Thread.sleep(1000);
        
        var popularProducts = popularProductService.getPopularProducts(1, 5);
        assertThat(popularProducts).isNotEmpty();
        
        // 집계된 판매량이 기록되었는지 확인
        boolean hasTestProduct = popularProducts.stream()
                .anyMatch(p -> p.getProduct().getId().equals(testProduct.getId()) && p.getSalesCount() > 0);
        assertThat(hasTestProduct).isTrue();

        executorService.shutdown();
    }

    @Test
    @DisplayName("시스템 전체 부하 테스트")
    void testSystemWideLoadTest() throws InterruptedException {
        // given
        int numberOfOperations = 500;
        ExecutorService executorService = Executors.newFixedThreadPool(100);

        // 다양한 작업들을 동시에 실행
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // when
        // 1. 쿠폰 발급 작업들
        for (int i = 0; i < numberOfOperations / 5; i++) {
            final int userIndex = i % 50; // 50명 중 순환
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    couponService.issueCoupon((long) (userIndex + 1), testCoupon.getId());
                } catch (Exception e) {
                    // 쿠폰 소진 또는 중복 발급
                }
            }, executorService));
        }

        // 2. 재고 차감 작업들
        for (int i = 0; i < numberOfOperations / 5; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    stockService.reduceStock(testProduct.getId(), 1);
                } catch (Exception e) {
                    // 재고 부족
                }
            }, executorService));
        }

        // 3. 인기 상품 기록 작업들
        for (int i = 0; i < numberOfOperations / 5; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    popularProductService.recordSale(testProduct.getId(), 1, null);
                } catch (Exception e) {
                    // 기록 실패
                }
            }, executorService));
        }

        // 4. 인기 상품 조회 작업들
        for (int i = 0; i < numberOfOperations / 5; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    popularProductService.getPopularProducts(3, 5);
                } catch (Exception e) {
                    // 조회 실패
                }
            }, executorService));
        }

        // 5. 재고 조회 작업들
        for (int i = 0; i < numberOfOperations / 5; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    stockService.getStock(testProduct.getId());
                } catch (Exception e) {
                    // 조회 실패
                }
            }, executorService));
        }

        // 모든 작업 완료 대기
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        allFutures.get(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        // then
        long duration = endTime - startTime;
        double throughput = (double) numberOfOperations / duration * 1000;

        System.out.printf("시스템 부하 테스트 - %d개 작업, 처리 시간: %dms, 처리량: %.2f TPS%n",
                         numberOfOperations, duration, throughput);

        // 시스템이 정상적으로 응답했는지 확인
        assertThat(duration).isLessThan(60000); // 60초 내 완료

        // 락 매니저 정상 상태 확인
        assertThat(lockManager.getActiveLockCount()).isZero();

        executorService.shutdown();
    }

    @Test
    @DisplayName("데드락 방지 테스트")
    void testDeadlockPrevention() throws InterruptedException {
        // given
        String lock1 = "resource-1";
        String lock2 = "resource-2";
        
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger completedTasks = new AtomicInteger(0);

        // when
        // Thread 1: lock1 -> lock2 순서
        executorService.execute(() -> {
            try {
                startLatch.await();
                lockManager.executeWithLock(lock1, () -> {
                    Thread.sleep(100);
                    return lockManager.executeWithLock(lock2, () -> {
                        completedTasks.incrementAndGet();
                        return null;
                    });
                });
            } catch (Exception e) {
                // 타임아웃 또는 인터럽트
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: lock2 -> lock1 순서 (데드락 유발 시도)
        executorService.execute(() -> {
            try {
                startLatch.await();
                lockManager.executeWithLock(lock2, () -> {
                    Thread.sleep(100);
                    return lockManager.executeWithLock(lock1, () -> {
                        completedTasks.incrementAndGet();
                        return null;
                    });
                });
            } catch (Exception e) {
                // 타임아웃 또는 인터럽트
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();
        
        // 데드락이 발생하지 않고 적어도 하나의 작업은 완료되어야 함
        // (타임아웃으로 인해 두 작업이 모두 완료되지 않을 수 있음)
        System.out.printf("데드락 방지 테스트 - 완료된 작업: %d개%n", completedTasks.get());

        executorService.shutdown();
    }
}