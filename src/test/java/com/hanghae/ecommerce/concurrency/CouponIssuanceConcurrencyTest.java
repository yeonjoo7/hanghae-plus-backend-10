package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.application.coupon.CouponService;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 동시성 테스트
 * MySQL 환경에서 실제 API 엔드포인트를 사용하여 동시성 제어를 검증합니다.
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
    System.out.println("First user ID: " + testUsers.get(0).getId());
    System.out.println("Last user ID: " + testUsers.get(testUsers.size() - 1).getId());
  }

  @Test
  @DisplayName("선착순 쿠폰 발급 - 100개 쿠폰에 1000명 동시 요청 시 정확히 100명만 발급")
  void testConcurrentCouponIssuance() throws Exception {
    // given
    int totalUsers = testUsers.size();
    int expectedSuccessCount = 100;

    ExecutorService executor = Executors.newFixedThreadPool(50);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(totalUsers);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);
    AtomicInteger alreadyIssuedException = new AtomicInteger(0);
    AtomicInteger soldOutException = new AtomicInteger(0);
    AtomicInteger otherException = new AtomicInteger(0);

    // when: 1000명이 동시에 쿠폰 발급 시도 (서비스 직접 호출)
    for (User user : testUsers) {
      executor.submit(() -> {
        try {
          // 모든 스레드가 동시에 시작하도록 대기
          startLatch.await();

          // 서비스 직접 호출
          couponService.issueCoupon(testCoupon.getId(), user.getId());
          successCount.incrementAndGet();

        } catch (Exception e) {
          // 쿠폰 소진 또는 중복 발급 등의 이유로 실패는 정상
          failureCount.incrementAndGet();

          // 예외 타입별로 카운트
          String exceptionMsg = e.getMessage();
          if (exceptionMsg != null) {
            if (exceptionMsg.contains("이미 발급받은")) {
              alreadyIssuedException.incrementAndGet();
            } else if (exceptionMsg.contains("소진") || exceptionMsg.contains("찾을 수 없습니다")) {
              soldOutException.incrementAndGet();
            } else {
              otherException.incrementAndGet();
            }
          } else {
            otherException.incrementAndGet();
          }
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
    System.out.println("  - Already issued: " + alreadyIssuedException.get());
    System.out.println("  - Sold out: " + soldOutException.get());
    System.out.println("  - Other: " + otherException.get());
    System.out.println("Total: " + (successCount.get() + failureCount.get()));
    System.out.println("Expected success: " + expectedSuccessCount);

    // DB에서 실제 발급된 쿠폰 수 확인
    long actualIssuedInDb = testUsers.stream()
        .filter(user -> !userCouponRepository.findByUserIdAndCouponId(user.getId(), testCoupon.getId()).isEmpty())
        .count();
    System.out.println("Actual user_coupons in DB: " + actualIssuedInDb);

    assertThat(completed).isTrue();
    assertThat(successCount.get()).isEqualTo(expectedSuccessCount);
    assertThat(successCount.get() + failureCount.get()).isEqualTo(totalUsers);

    // DB 검증: 쿠폰 발급 수량 확인
    Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
    System.out.println("DB issued quantity: " + updatedCoupon.getIssuedQuantity().getValue());
    assertThat(updatedCoupon.getIssuedQuantity().getValue()).isEqualTo(expectedSuccessCount);
  }

  @Test
  @DisplayName("선착순 쿠폰 발급 - 소량 쿠폰(10개)에 대한 대규모 동시 요청")
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

    Coupon updatedCoupon = couponRepository.findById(smallCoupon.getId()).orElseThrow();
    assertThat(updatedCoupon.getIssuedQuantity().getValue()).isEqualTo(10);
  }

  @Test
  @DisplayName("동일 사용자가 같은 쿠폰을 중복 발급 시도 - 1번만 성공")
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
  }
}
