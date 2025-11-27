package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.application.coupon.CouponService;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 동시성 테스트
 * 분산 락을 사용한 선착순 쿠폰 발급의 동시성 제어를 검증합니다.
 */
@DisplayName("쿠폰 발급 동시성 테스트")
class CouponIssuanceConcurrencyTest extends BaseConcurrencyTest {

  @Autowired
  private CouponService couponService;

  private Coupon testCoupon;
  private List<User> testUsers;

  @BeforeEach
  void setUp() {
    // 테스트 데이터 생성
    testCoupon = createCoupon("선착순 100개 쿠폰", 100, 10);
    testUsers = createUsers(1000); // 1000명의 사용자

    System.out.println("=== Setup Complete ===");
    System.out.println("Coupon ID: " + testCoupon.getId());
    System.out.println("Total users created: " + testUsers.size());
  }

  @Test
  @DisplayName("선착순 쿠폰 발급 - 100개 쿠폰에 1000명 동시 요청 시 정확히 100명만 발급")
  @DirtiesContext
  void testConcurrentCouponIssuance() throws Exception {
    // given
    int totalUsers = testUsers.size();
    int expectedSuccessCount = 100;

    ExecutorService executor = Executors.newFixedThreadPool(50);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(totalUsers);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    // when: 1000명이 동시에 쿠폰 발급 시도
    for (User user : testUsers) {
      executor.submit(() -> {
        try {
          // 모든 스레드가 동시에 시작하도록 대기
          startLatch.await();

          couponService.issueCoupon(testCoupon.getId(), user.getId());
          successCount.incrementAndGet();

        } catch (Exception e) {
          // 쿠폰 소진 또는 중복 발급 등의 이유로 실패는 정상
          failureCount.incrementAndGet();
        } finally {
          doneLatch.countDown();
        }
      });
    }

    // 모든 스레드 동시 시작
    startLatch.countDown();

    // 모든 요청 완료 대기 (최대 30초)
    boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 정확히 100명만 발급 성공
    System.out.println("=== Test Results ===");
    System.out.println("Success count: " + successCount.get());
    System.out.println("Failure count: " + failureCount.get());
    System.out.println("Total: " + (successCount.get() + failureCount.get()));

    assertThat(completed).isTrue();
    assertThat(successCount.get()).isEqualTo(expectedSuccessCount);
    assertThat(successCount.get() + failureCount.get()).isEqualTo(totalUsers);

    // JPA를 사용한 DB 검증
    Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
    assertThat(updatedCoupon.getIssuedQuantity().getValue()).isEqualTo(expectedSuccessCount);

    // 실제 발급된 UserCoupon 수 확인
    List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(testCoupon.getId());
    assertThat(issuedCoupons).hasSize(expectedSuccessCount);

    // 모든 발급된 쿠폰이 서로 다른 사용자에게 발급되었는지 확인
    long uniqueUserCount = issuedCoupons.stream()
        .map(UserCoupon::getUserId)
        .distinct()
        .count();
    assertThat(uniqueUserCount).isEqualTo(expectedSuccessCount);
  }

  @Test
  @DisplayName("선착순 쿠폰 발급 - 소량 쿠폰(10개)에 대한 대규모 동시 요청")
  @DirtiesContext
  void testConcurrentCouponIssuanceWithSmallQuantity() throws Exception {
    // given: 10개 한정 쿠폰
    Coupon smallCoupon = createCoupon("초특가 10개 한정", 10, 20);
    List<User> manyUsers = createUsers(500); // 500명 요청

    ExecutorService executor = Executors.newFixedThreadPool(50);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(manyUsers.size());
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 500명이 동시에 10개 쿠폰 발급 시도
    for (User user : manyUsers) {
      executor.submit(() -> {
        try {
          startLatch.await();
          couponService.issueCoupon(smallCoupon.getId(), user.getId());
          successCount.incrementAndGet();
        } catch (Exception e) {
          // 실패는 정상
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 정확히 10명만 성공
    assertThat(successCount.get()).isEqualTo(10);

    // JPA를 사용한 DB 검증
    Coupon updatedCoupon = couponRepository.findById(smallCoupon.getId()).orElseThrow();
    assertThat(updatedCoupon.getIssuedQuantity().getValue()).isEqualTo(10);

    List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(smallCoupon.getId());
    assertThat(issuedCoupons).hasSize(10);
  }

  @Test
  @DisplayName("동일 사용자가 같은 쿠폰을 중복 발급 시도 - 1번만 성공")
  @DirtiesContext
  void testDuplicateCouponIssuanceForSameUser() throws Exception {
    // given
    User user = testUsers.get(0);
    int attemptCount = 10;

    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(attemptCount);
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 동일 사용자가 10번 동시 요청
    for (int i = 0; i < attemptCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();
          couponService.issueCoupon(testCoupon.getId(), user.getId());
          successCount.incrementAndGet();
        } catch (Exception e) {
          // 중복 발급 실패는 정상
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 1번만 성공
    assertThat(successCount.get()).isEqualTo(1);

    // JPA를 사용한 DB 검증
    List<UserCoupon> userCoupons = userCouponRepository.findByUserIdAndCouponId(
        user.getId(), testCoupon.getId());
    assertThat(userCoupons).hasSize(1);
  }

  @Test
  @DisplayName("분산 락 동작 검증 - 동시 요청 시 순차적으로 처리됨")
  @DirtiesContext
  void testDistributedLockBehavior() throws Exception {
    // given
    Coupon limitedCoupon = createCoupon("분산락 테스트 쿠폰", 50, 10);
    List<User> users = createUsers(100);

    ExecutorService executor = Executors.newFixedThreadPool(20);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(users.size());
    AtomicInteger successCount = new AtomicInteger(0);
    ConcurrentHashMap<Long, Long> threadTimestamps = new ConcurrentHashMap<>();

    // when: 100명이 동시에 요청
    for (User user : users) {
      executor.submit(() -> {
        try {
          startLatch.await();
          long startTime = System.nanoTime();
          couponService.issueCoupon(limitedCoupon.getId(), user.getId());
          long endTime = System.nanoTime();

          threadTimestamps.put(Thread.currentThread().getId(), endTime - startTime);
          successCount.incrementAndGet();
        } catch (Exception e) {
          // 실패는 정상
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 정확히 50명만 성공
    assertThat(successCount.get()).isEqualTo(50);

    // 분산 락이 제대로 동작했는지 검증
    Coupon updatedCoupon = couponRepository.findById(limitedCoupon.getId()).orElseThrow();
    assertThat(updatedCoupon.getIssuedQuantity().getValue()).isEqualTo(50);

    // 실제 발급된 쿠폰 수 확인
    List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(limitedCoupon.getId());
    assertThat(issuedCoupons).hasSize(50);

    // 중복 발급이 없는지 확인
    long uniqueUsers = issuedCoupons.stream()
        .map(UserCoupon::getUserId)
        .distinct()
        .count();
    assertThat(uniqueUsers).isEqualTo(50);

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
  @DisplayName("쿠폰 소진 후 추가 요청 - 모두 실패")
  @DirtiesContext
  void testCouponIssuanceAfterSoldOut() throws Exception {
    // given: 5개 한정 쿠폰
    Coupon tinyCoupon = createCoupon("5개 한정 쿠폰", 5, 10);
    List<User> firstBatch = createUsers(5);
    List<User> secondBatch = createUsers(10);

    // when: 먼저 5명이 쿠폰을 모두 소진
    for (User user : firstBatch) {
      couponService.issueCoupon(tinyCoupon.getId(), user.getId());
    }

    // then: 쿠폰이 모두 소진됨
    Coupon soldOutCoupon = couponRepository.findById(tinyCoupon.getId()).orElseThrow();
    assertThat(soldOutCoupon.getIssuedQuantity().getValue()).isEqualTo(5);

    // when: 추가로 10명이 동시에 요청
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(secondBatch.size());
    AtomicInteger failureCount = new AtomicInteger(0);

    for (User user : secondBatch) {
      executor.submit(() -> {
        try {
          startLatch.await();
          couponService.issueCoupon(tinyCoupon.getId(), user.getId());
        } catch (Exception e) {
          failureCount.incrementAndGet();
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 모든 요청이 실패
    assertThat(failureCount.get()).isEqualTo(secondBatch.size());

    // 여전히 5개만 발급되어 있음
    List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(tinyCoupon.getId());
    assertThat(issuedCoupons).hasSize(5);
  }
}
