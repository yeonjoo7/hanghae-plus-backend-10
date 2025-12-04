package com.hanghae.ecommerce.infrastructure.coupon;

import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 대기열 서비스 테스트
 * 
 * Redis Sorted Set 기반 대기열 관리 기능을 검증합니다.
 */
@DisplayName("CouponQueueService 테스트")
class CouponQueueServiceTest extends BaseIntegrationTest {

    @Autowired
    private CouponQueueService couponQueueService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Long testCouponId;

    @BeforeEach
    void setUp() {
        testCouponId = 1L;
        couponQueueService.clearQueue(testCouponId);
    }

    @Test
    @DisplayName("대기열에 사용자 추가")
    void testEnqueue() {
        // given
        Long userId = 100L;

        // when
        long rank = couponQueueService.enqueue(testCouponId, userId);

        // then
        assertThat(rank).isEqualTo(1); // 첫 번째 사용자
        assertThat(couponQueueService.getQueueSize(testCouponId)).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 사용자 대기열 추가 - 선착순 순서 확인")
    void testEnqueueMultipleUsers() throws InterruptedException {
        // given
        Long userId1 = 100L;
        Long userId2 = 200L;
        Long userId3 = 300L;

        // when - 시간차를 두고 추가
        long rank1 = couponQueueService.enqueue(testCouponId, userId1);
        Thread.sleep(10); // 시간차
        long rank2 = couponQueueService.enqueue(testCouponId, userId2);
        Thread.sleep(10);
        long rank3 = couponQueueService.enqueue(testCouponId, userId3);

        // then
        assertThat(rank1).isEqualTo(1);
        assertThat(rank2).isEqualTo(2);
        assertThat(rank3).isEqualTo(3);
        assertThat(couponQueueService.getQueueSize(testCouponId)).isEqualTo(3);
    }

    @Test
    @DisplayName("중복 대기열 추가 - 기존 순위 반환")
    void testEnqueueDuplicate() {
        // given
        Long userId = 100L;
        couponQueueService.enqueue(testCouponId, userId);

        // when - 같은 사용자가 다시 추가 시도
        long rank = couponQueueService.enqueue(testCouponId, userId);

        // then
        assertThat(rank).isEqualTo(1); // 기존 순위 반환
        assertThat(couponQueueService.getQueueSize(testCouponId)).isEqualTo(1); // 중복 추가 안됨
    }

    @Test
    @DisplayName("발급 완료 사용자는 대기열에 추가되지 않음")
    void testEnqueueAlreadyIssued() {
        // given
        Long userId = 100L;
        couponQueueService.markAsIssued(testCouponId, userId);

        // when
        long rank = couponQueueService.enqueue(testCouponId, userId);

        // then
        assertThat(rank).isEqualTo(-1); // 이미 발급됨
    }

    @Test
    @DisplayName("상위 N명 조회")
    void testGetTopUsers() {
        // given
        for (int i = 1; i <= 10; i++) {
            couponQueueService.enqueue(testCouponId, (long) i);
        }

        // when
        Set<Object> topUsers = couponQueueService.getTopUsers(testCouponId, 5);

        // then
        assertThat(topUsers).hasSize(5);
    }

    @Test
    @DisplayName("발급 완료 처리")
    void testMarkAsIssued() {
        // given
        Long userId = 100L;
        couponQueueService.enqueue(testCouponId, userId);
        assertThat(couponQueueService.getQueueSize(testCouponId)).isEqualTo(1);

        // when
        couponQueueService.markAsIssued(testCouponId, userId);

        // then
        assertThat(couponQueueService.isAlreadyIssued(testCouponId, userId)).isTrue();
        assertThat(couponQueueService.getQueueSize(testCouponId)).isEqualTo(0); // 대기열에서 제거됨
    }

    @Test
    @DisplayName("수량 관리 - 초기화 및 차감")
    void testQuantityManagement() {
        // given
        int totalQuantity = 100;

        // when
        couponQueueService.initializeQuantity(testCouponId, totalQuantity);
        int remaining1 = couponQueueService.getRemainingQuantity(testCouponId);

        // then
        assertThat(remaining1).isEqualTo(100);

        // when - 수량 차감
        int remaining2 = couponQueueService.decrementQuantity(testCouponId);

        // then
        assertThat(remaining2).isEqualTo(99);
        assertThat(couponQueueService.getRemainingQuantity(testCouponId)).isEqualTo(99);
    }

    @Test
    @DisplayName("동시성 테스트 - 여러 사용자가 동시에 대기열 추가")
    void testConcurrentEnqueue() throws InterruptedException {
        // given
        int concurrentUsers = 50;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 1; i <= concurrentUsers; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long rank = couponQueueService.enqueue(testCouponId, (long) userId);
                    if (rank > 0) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("대기열 추가 실패: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(concurrentUsers);
        assertThat(couponQueueService.getQueueSize(testCouponId)).isEqualTo(concurrentUsers);
    }
}

