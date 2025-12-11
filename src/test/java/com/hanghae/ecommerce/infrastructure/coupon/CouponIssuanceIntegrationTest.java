package com.hanghae.ecommerce.infrastructure.coupon;

import com.hanghae.ecommerce.application.coupon.CouponService;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.DiscountPolicy;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
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
 * 쿠폰 발급 통합 테스트
 * 
 * Redis 기반 선착순 쿠폰 발급 시스템의 전체 플로우를 검증합니다.
 */
@DisplayName("쿠폰 발급 통합 테스트 (Redis 기반)")
class CouponIssuanceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponQueueService couponQueueService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    private Coupon testCoupon;
    private List<User> testUsers;

    @BeforeEach
    void setUp() {
        // 테스트 쿠폰 생성
        testCoupon = Coupon.create(
                "선착순 테스트 쿠폰",
                DiscountPolicy.rate(10),
                Quantity.of(100), // 100개만 발급
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(7));
        testCoupon = couponRepository.save(testCoupon);

        // Redis 대기열 초기화
        couponQueueService.clearQueue(testCoupon.getId());

        // 테스트 사용자 생성
        testUsers = createTestUsers(200); // 200명의 사용자 (쿠폰은 100개만 있음)
    }

    private List<User> createTestUsers(int count) {
        List<User> users = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            // 전화번호 형식: 010-1234-5678 (마지막 4자리만 변경)
            String phone = String.format("010-1234-%04d", i % 10000);
            User user = User.create(
                    "testuser" + i + "_" + timestamp + "@test.com",
                    "테스트유저" + i,
                    phone);
            users.add(userRepository.save(user));
        }

        return users;
    }

    /**
     * 테스트 환경에서 스케줄러 대신 직접 대기열 처리
     *
     * 실제 운영 환경에서는 CouponIssuanceScheduler가 이 작업을 수행합니다.
     */
    private void processCouponQueueManually(Long couponId, int maxIssuance) {
        int issuedCount = 0;

        while (issuedCount < maxIssuance) {
            Set<Object> topUsers = couponQueueService.getTopUsers(couponId, 10);
            if (topUsers == null || topUsers.isEmpty()) {
                break;
            }

            for (Object userIdObj : topUsers) {
                if (issuedCount >= maxIssuance) {
                    break;
                }

                try {
                    Long userId = Long.valueOf(userIdObj.toString());

                    if (couponQueueService.isAlreadyIssued(couponId, userId)) {
                        couponQueueService.removeFromQueue(couponId, userId);
                        continue;
                    }

                    // 실제 쿠폰 발급
                    couponService.issueCouponFromQueue(couponId, userId);
                    couponQueueService.markAsIssued(couponId, userId);
                    issuedCount++;
                } catch (Exception e) {
                    // 발급 실패 시 대기열에서 제거
                    couponQueueService.removeFromQueue(couponId, Long.valueOf(userIdObj.toString()));
                }
            }
        }
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 100개 쿠폰에 200명 동시 요청 시 정확히 100명만 발급")
    void testFirstComeFirstServedCouponIssuance() throws InterruptedException {
        // given
        int totalCoupons = 100;
        int totalUsers = 200;
        Long couponId = testCoupon.getId();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalUsers);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // when - 200명이 동시에 쿠폰 발급 요청
        for (User user : testUsers) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long rank = couponService.requestCouponIssue(couponId, user.getId());
                    requestCount.incrementAndGet();
                    if (rank > 0) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 중복 발급 등은 정상적인 실패
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then - 모든 요청이 대기열에 추가되었는지 확인
        assertThat(requestCount.get()).isEqualTo(totalUsers);

        // 테스트 환경에서는 스케줄러가 비활성화되어 있으므로 직접 대기열 처리
        processCouponQueueManually(couponId, totalCoupons);

        // 실제 발급된 쿠폰 수 확인
        int issuedCount = userCouponRepository.findByCouponId(couponId).size();
        assertThat(issuedCount)
                .as("100개 쿠폰에 대해 정확히 100명만 발급되어야 함")
                .isEqualTo(totalCoupons);

        System.out.println("=== 선착순 쿠폰 발급 테스트 결과 ===");
        System.out.printf("총 요청 수: %d%n", requestCount.get());
        System.out.printf("대기열 추가 성공: %d%n", successCount.get());
        System.out.printf("실제 발급 수: %d (기대값: %d)%n", issuedCount, totalCoupons);
    }

    @Test
    @DisplayName("대기열 순위 조회")
    void testGetQueueRank() {
        // given
        Long couponId = testCoupon.getId();
        Long userId1 = testUsers.get(0).getId();
        Long userId2 = testUsers.get(1).getId();
        Long userId3 = testUsers.get(2).getId();

        // when
        long rank1 = couponService.requestCouponIssue(couponId, userId1);
        long rank2 = couponService.requestCouponIssue(couponId, userId2);
        long rank3 = couponService.requestCouponIssue(couponId, userId3);

        // then
        assertThat(rank1).isEqualTo(1);
        assertThat(rank2).isEqualTo(2);
        assertThat(rank3).isEqualTo(3);

        // 순위 조회 API 테스트
        long queriedRank1 = couponService.getQueueRank(couponId, userId1);
        assertThat(queriedRank1).isEqualTo(1);
    }

    @Test
    @DisplayName("중복 발급 방지")
    void testDuplicateIssuancePrevention() {
        // given
        Long couponId = testCoupon.getId();
        Long userId = testUsers.get(0).getId();

        // when - 첫 번째 요청
        long rank1 = couponService.requestCouponIssue(couponId, userId);

        // when - 두 번째 요청 (중복)
        try {
            long rank2 = couponService.requestCouponIssue(couponId, userId);
            // 중복 요청은 기존 순위를 반환하거나 예외 발생
            assertThat(rank2).isEqualTo(rank1);
        } catch (Exception e) {
            // 예외 발생도 정상 (이미 대기열에 있음)
        }

        // then
        assertThat(couponQueueService.getQueueSize(couponId)).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰 소진 후 추가 요청 처리")
    void testSoldOutCoupon() {
        // given
        Long couponId = testCoupon.getId();
        int totalCoupons = 100;

        // when - 100명이 먼저 요청
        for (int i = 0; i < totalCoupons; i++) {
            couponService.requestCouponIssue(couponId, testUsers.get(i).getId());
        }

        // 대기열 크기 확인 (100명이 모두 추가되었는지)
        assertThat(couponQueueService.getQueueSize(couponId)).isEqualTo(totalCoupons);

        // when - 추가 요청 (소진 전이지만 대기열에는 추가됨)
        long rank = couponService.requestCouponIssue(couponId, testUsers.get(100).getId());

        // then - 대기열에는 추가됨 (101번째)
        assertThat(rank).isEqualTo(totalCoupons + 1);
        assertThat(couponQueueService.getQueueSize(couponId)).isEqualTo(totalCoupons + 1);

        // 테스트 환경에서는 스케줄러가 비활성화되어 있으므로 직접 대기열 처리
        // 101명이 대기열에 있지만, 쿠폰은 100개만 있으므로 100개만 발급되어야 함
        processCouponQueueManually(couponId, totalCoupons + 10); // 충분히 큰 수로 호출

        // 실제 발급은 100개만 되어야 함
        int issuedCount = userCouponRepository.findByCouponId(couponId).size();
        assertThat(issuedCount).isEqualTo(totalCoupons);
    }
}

