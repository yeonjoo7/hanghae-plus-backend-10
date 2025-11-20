package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.application.service.CouponService;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.CouponState;
import com.hanghae.ecommerce.domain.coupon.DiscountPolicy;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.UserState;
import com.hanghae.ecommerce.domain.user.UserType;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
 * 쿠폰 발급 동시성 제어 테스트
 * 
 * 선착순 쿠폰 발급에서 Race Condition이 발생하지 않는지 검증합니다.
 */
import com.hanghae.ecommerce.support.BaseIntegrationTest;

class CouponConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LockManager lockManager;

    private Coupon testCoupon;
    private List<User> testUsers;

    @BeforeEach
    void setUp() {
        // 락 매니저 정리
        lockManager.clearAllLocks();

        // 고유한 타임스탬프로 테스트 데이터 생성
        long timestamp = System.currentTimeMillis();

        // 테스트 쿠폰 생성 (선착순 100명)
        testCoupon = Coupon.create(
                "선착순 10% 할인 쿠폰 " + timestamp,
                DiscountPolicy.rate(10),
                Quantity.of(100),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(7));
        testCoupon = couponRepository.save(testCoupon);

        // 테스트 사용자 생성 (1000명)
        testUsers = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            User user = User.create(
                    "user" + i + "_" + timestamp + "@test.com",
                    "테스트유저" + i,
                    "010-" + String.format("%04d", i) + "-5678");
            testUsers.add(userRepository.save(user));
        }
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 100개 쿠폰에 1000명이 동시 요청 시 정확히 100명만 발급")
    void testConcurrentCouponIssuance() throws InterruptedException {
        // given
        int totalCouponQuantity = 100;
        int totalUsers = 1000;
        int threadPoolSize = 50;

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        // when
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalUsers; i++) {
            final User user = testUsers.get(i);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // 모든 스레드가 동시에 시작하도록 대기
                    startLatch.await();

                    // 쿠폰 발급 시도
                    UserCoupon issuedCoupon = couponService.issueCoupon(user.getId(), testCoupon.getId());

                    if (issuedCoupon != null) {
                        successCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 스레드 동시 시작
        Thread.sleep(100); // 모든 스레드가 대기 상태가 되도록 잠시 대기
        startLatch.countDown();

        // 모든 작업 완료 대기 (최대 30초)
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();

        // 성공한 발급 건수는 정확히 쿠폰 수량과 일치해야 함
        assertThat(successCount.get()).isEqualTo(totalCouponQuantity);

        // 실패한 건수는 나머지와 일치해야 함
        assertThat(failureCount.get()).isEqualTo(totalUsers - totalCouponQuantity);

        // 실제 DB에서 발급된 쿠폰 수량 확인
        List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(testCoupon.getId());
        assertThat(issuedCoupons).hasSize(totalCouponQuantity);

        // 쿠폰의 발급 수량이 정확히 업데이트되었는지 확인
        Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity().getValue()).isEqualTo(totalCouponQuantity);
        assertThat(updatedCoupon.getRemainingQuantity().getValue()).isZero();

        // 중복 발급이 없는지 확인 (사용자별로 1개씩만 발급)
        List<Long> userIds = issuedCoupons.stream()
                .map(UserCoupon::getUserId)
                .distinct()
                .toList();
        assertThat(userIds).hasSize(totalCouponQuantity);

        executorService.shutdown();

        // 실패 원인 분석을 위한 로그
        System.out.printf("성공: %d, 실패: %d, 예외 종류: %d%n",
                successCount.get(), failureCount.get(), exceptions.size());

        // 대부분의 실패는 "쿠폰이 모두 소진되었습니다" 또는 "이미 발급받은 쿠폰입니다" 여야 함
        long soldOutExceptions = exceptions.stream()
                .filter(e -> e.getMessage().contains("소진"))
                .count();
        long duplicateExceptions = exceptions.stream()
                .filter(e -> e.getMessage().contains("이미 발급"))
                .count();

        System.out.printf("소진 예외: %d, 중복 예외: %d%n", soldOutExceptions, duplicateExceptions);
    }

    @Test
    @DisplayName("쿠폰 발급 중 동시 요청 성능 테스트")
    void testCouponIssuancePerformance() throws InterruptedException {
        // given
        int concurrentUsers = 100;
        int threadPoolSize = 20;

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        long startTime = System.currentTimeMillis();

        // when
        for (int i = 0; i < concurrentUsers; i++) {
            final User user = testUsers.get(i);

            executorService.execute(() -> {
                try {
                    startLatch.await();
                    couponService.issueCoupon(user.getId(), testCoupon.getId());
                } catch (Exception e) {
                    // 성능 테스트이므로 예외는 무시
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

        long duration = endTime - startTime;
        double throughput = (double) concurrentUsers / duration * 1000; // TPS

        System.out.printf("동시 요청 %d건 처리 시간: %dms, 처리량: %.2f TPS%n",
                concurrentUsers, duration, throughput);

        // 성능 기준: 100건 요청을 30초 내에 처리해야 함
        assertThat(duration).isLessThan(30000);

        executorService.shutdown();
    }

    @Test
    @DisplayName("다양한 시나리오의 쿠폰 발급 동시성 테스트")
    void testVariousConcurrencyScenarios() throws InterruptedException {
        // given
        // 소량 쿠폰 생성 (5개)
        final Coupon limitedCoupon = couponRepository.save(
                Coupon.create(
                        "초소량 쿠폰",
                        DiscountPolicy.amount(Money.of(1000)),
                        Quantity.of(5),
                        LocalDateTime.now(),
                        LocalDateTime.now().plusHours(1)));

        int concurrentUsers = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < concurrentUsers; i++) {
            final User user = testUsers.get(i);

            executorService.execute(() -> {
                try {
                    startLatch.await();
                    couponService.issueCoupon(user.getId(), limitedCoupon.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패는 정상 (쿠폰 수량 부족)
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(5, TimeUnit.SECONDS);

        // then
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(5);

        // 실제 발급된 쿠폰 수 확인
        List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(limitedCoupon.getId());
        assertThat(issuedCoupons).hasSize(5);

        executorService.shutdown();
    }

    @Test
    @DisplayName("락 타임아웃 테스트")
    void testLockTimeout() {
        // given
        String lockKey = "test-lock";

        // when & then
        // 첫 번째 락 획득
        assertThat(lockManager.executeWithLock(lockKey, () -> {
            // 두 번째 스레드에서 같은 락 획득 시도 (타임아웃 1초)
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return lockManager.tryLock(lockKey, 1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    return false;
                }
            });

            try {
                Thread.sleep(2000); // 2초 대기
                return future.get();
            } catch (Exception e) {
                return false;
            }
        })).isFalse(); // 타임아웃으로 인해 락 획득 실패해야 함
    }

    @Test
    @DisplayName("락 매니저 메모리 사용량 테스트")
    void testLockManagerMemoryUsage() throws InterruptedException {
        // given
        int lockCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(lockCount);

        // when
        for (int i = 0; i < lockCount; i++) {
            final String lockKey = "lock-" + i;

            executorService.execute(() -> {
                try {
                    lockManager.executeWithLock(lockKey, () -> {
                        Thread.sleep(10); // 짧은 작업 시뮬레이션
                        return null;
                    });
                } catch (Exception e) {
                    // 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);

        // then
        // 작업 완료 후 활성 락 수가 0이어야 함 (메모리 누수 방지)
        int activeLocks = lockManager.getActiveLockCount();
        assertThat(activeLocks).isZero();

        executorService.shutdown();
    }
}