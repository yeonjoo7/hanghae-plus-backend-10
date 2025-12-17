package com.hanghae.ecommerce.infrastructure.coupon;

import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
 * Redis List 기반 대기열 관리 기능을 검증합니다.
 * - RPUSH로 대기열에 추가 (선착순 보장)
 * - LPOP으로 대기열에서 꺼냄 (FIFO)
 */
@DisplayName("CouponQueueService 테스트")
class CouponQueueServiceTest extends BaseIntegrationTest {

  @Autowired
  private CouponQueueService couponQueueService;

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
  @DisplayName("발급 완료 처리 - 스케줄러 워크플로우 (dequeue → markAsIssued)")
  void testMarkAsIssued() {
    // given
    Long userId = 100L;
    couponQueueService.enqueue(testCouponId, userId);
    assertThat(couponQueueService.getQueueSize(testCouponId)).isEqualTo(1);

    // when - 스케줄러 워크플로우: dequeue로 빼고, markAsIssued로 발급 완료 표시
    String dequeuedUserId = couponQueueService.dequeue(testCouponId);
    couponQueueService.markAsIssued(testCouponId, Long.valueOf(dequeuedUserId));

    // then
    assertThat(dequeuedUserId).isEqualTo(userId.toString());
    assertThat(couponQueueService.isAlreadyIssued(testCouponId, userId)).isTrue();
    assertThat(couponQueueService.getQueueSize(testCouponId)).isEqualTo(0); // dequeue로 제거됨
  }

  @Test
  @DisplayName("대기열에서 사용자 꺼내기 (FIFO)")
  void testDequeue() {
    // given
    Long userId1 = 100L;
    Long userId2 = 200L;
    Long userId3 = 300L;
    couponQueueService.enqueue(testCouponId, userId1);
    couponQueueService.enqueue(testCouponId, userId2);
    couponQueueService.enqueue(testCouponId, userId3);

    // when & then - FIFO 순서로 꺼내지는지 확인
    assertThat(couponQueueService.dequeue(testCouponId)).isEqualTo(userId1.toString());
    assertThat(couponQueueService.dequeue(testCouponId)).isEqualTo(userId2.toString());
    assertThat(couponQueueService.dequeue(testCouponId)).isEqualTo(userId3.toString());
    assertThat(couponQueueService.dequeue(testCouponId)).isNull(); // 빈 큐
  }

  @Test
  @DisplayName("빈 대기열에서 dequeue 시 null 반환")
  void testDequeueFromEmptyQueue() {
    // when
    String result = couponQueueService.dequeue(testCouponId);

    // then
    assertThat(result).isNull();
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
