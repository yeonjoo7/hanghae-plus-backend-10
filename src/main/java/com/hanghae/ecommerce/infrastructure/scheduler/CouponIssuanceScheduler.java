package com.hanghae.ecommerce.infrastructure.scheduler;

import com.hanghae.ecommerce.application.coupon.CouponService;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.infrastructure.coupon.CouponQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 쿠폰 발급 스케줄러
 * 
 * Redis 대기열에 있는 사용자들을 주기적으로 처리하여 실제 쿠폰을 발급합니다.
 * 
 * ## 처리 방식
 * 1. 활성 쿠폰 목록 조회
 * 2. 각 쿠폰의 대기열에서 상위 N명 조회
 * 3. 남은 수량만큼 실제 발급 처리
 * 4. 발급 완료된 사용자는 발급 완료 Set에 추가
 * 
 * ## 스케줄 설정
 * - fixedDelay: 1초마다 실행 (대기열 처리)
 * - 초당 처리량: 배치 크기만큼 (기본 100명)
 * 
 * ## 테스트 환경
 * - 테스트 프로파일에서는 자동으로 비활성화됨
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.scheduler.coupon-issuance.enabled", havingValue = "true", matchIfMissing = false)
public class CouponIssuanceScheduler {

  private static final Logger log = LoggerFactory.getLogger(CouponIssuanceScheduler.class);

  private final CouponQueueService couponQueueService;
  private final CouponRepository couponRepository;
  private final CouponService couponService;
  private final RedisTemplate<String, Object> redisTemplate;

  // 배치 처리 크기 (한 번에 처리할 최대 인원)
  private static final int BATCH_SIZE = 100;

  public CouponIssuanceScheduler(
      CouponQueueService couponQueueService,
      CouponRepository couponRepository,
      CouponService couponService,
      RedisTemplate<String, Object> redisTemplate) {
    this.couponQueueService = couponQueueService;
    this.couponRepository = couponRepository;
    this.couponService = couponService;
    this.redisTemplate = redisTemplate;
  }

  /**
   * 쿠폰 발급 대기열 처리
   * 
   * 1초마다 실행되어 대기열에 있는 사용자들을 처리합니다.
   */
  @Scheduled(fixedDelay = 1000) // 1초마다 실행
  public void processCouponQueue() {
    try {
      // 발급 가능한 쿠폰만 조회 (발급 기간 내이고, 상태가 ISSUABLE이며, 아직 발급이 완료되지 않은 쿠폰)
      // 성능 최적화: 필요한 쿠폰만 조회하여 데이터베이스 부하 감소
      var issuableCoupons = couponRepository.findIssuableCouponsForScheduler(java.time.LocalDateTime.now());

      for (Coupon coupon : issuableCoupons) {
        processCouponQueue(coupon);
      }
    } catch (Exception e) {
      log.error("쿠폰 발급 대기열 처리 중 오류 발생", e);
    }
  }

  /**
   * 특정 쿠폰의 대기열 처리
   * 
   * @param coupon 쿠폰 정보
   */
  private void processCouponQueue(Coupon coupon) {
    Long couponId = coupon.getId();

    try {
      // 1. 남은 수량 확인
      java.util.Optional<Integer> remainingQuantityOpt = couponQueueService.getRemainingQuantity(couponId);
      int remainingQuantity;

      // Redis에 수량이 없으면 DB에서 초기화
      // SETNX를 사용하여 원자적으로 초기화하여 동시성 이슈 방지
      if (remainingQuantityOpt.isEmpty()) {
        int totalQuantity = coupon.getTotalQuantity().getValue();
        int issuedQuantity = coupon.getIssuedQuantity().getValue();
        remainingQuantity = totalQuantity - issuedQuantity;

        if (remainingQuantity <= 0) {
          return; // 이미 모두 발급됨
        }

        // SETNX를 사용하여 원자적으로 초기화 (이미 다른 스레드가 초기화했으면 false 반환)
        boolean initialized = couponQueueService.initializeQuantity(couponId, remainingQuantity, coupon.getEndDate());
        if (initialized) {
          // 초기화 성공 시 남은 수량 재조회
          remainingQuantityOpt = couponQueueService.getRemainingQuantity(couponId);
          remainingQuantity = remainingQuantityOpt.orElse(0);
        } else {
          // 다른 스레드가 이미 초기화한 경우, 재조회하여 최신 값 사용
          remainingQuantityOpt = couponQueueService.getRemainingQuantity(couponId);
          remainingQuantity = remainingQuantityOpt.orElse(0);
        }
      } else {
        remainingQuantity = remainingQuantityOpt.get();
      }

      if (remainingQuantity <= 0) {
        return; // 발급 가능한 수량 없음
      }

      // 2. 대기열에서 처리할 사용자 수 결정 (남은 수량과 배치 크기 중 작은 값)
      int processCount = Math.min(remainingQuantity, BATCH_SIZE);

      // 3. 대기열에서 상위 N명 조회
      Set<Object> topUsers = couponQueueService.getTopUsers(couponId, processCount);

      if (topUsers == null || topUsers.isEmpty()) {
        return; // 대기열이 비어있음
      }

      // 4. 각 사용자에 대해 발급 처리
      int successCount = 0;
      for (Object userIdObj : topUsers) {
        if (successCount >= remainingQuantity) {
          break; // 남은 수량 초과
        }

        try {
          Long userId = Long.valueOf(userIdObj.toString());

          // 이미 발급된 사용자는 건너뛰기
          if (couponQueueService.isAlreadyIssued(couponId, userId)) {
            couponQueueService.removeFromQueue(couponId, userId);
            continue;
          }

          // 실제 쿠폰 발급 처리 (Redis 기반)
          boolean issued = processCouponIssuance(couponId, userId);

          if (issued) {
            successCount++;
            couponQueueService.markAsIssued(couponId, userId, coupon.getEndDate());
            log.debug("쿠폰 발급 완료 - CouponId: {}, UserId: {}", couponId, userId);
          } else {
            // 발급 실패 (수량 부족 등) - 대기열에서 제거하지 않음 (재시도 가능)
            log.warn("쿠폰 발급 실패 - CouponId: {}, UserId: {}", couponId, userId);
          }
        } catch (Exception e) {
          log.error("쿠폰 발급 처리 중 오류 - CouponId: {}, UserId: {}", couponId, userIdObj, e);
        }
      }

      if (successCount > 0) {
        log.info("쿠폰 발급 배치 처리 완료 - CouponId: {}, 발급 수: {}", couponId, successCount);
      }
    } catch (Exception e) {
      log.error("쿠폰 대기열 처리 중 오류 - CouponId: {}", couponId, e);
    }
  }

  /**
   * 실제 쿠폰 발급 처리
   * 
   * @param couponId 쿠폰 ID
   * @param userId   사용자 ID
   * @return 발급 성공 여부
   */
  private boolean processCouponIssuance(Long couponId, Long userId) {
    try {
      // 수량 차감 시도
      int remaining = couponQueueService.decrementQuantity(couponId);

      if (remaining < 0) {
        return false; // 수량 부족
      }

      // 실제 DB에 쿠폰 발급 (Redis 기반 발급 서비스 사용)
      couponService.issueCouponFromQueue(couponId, userId);
      return true;
    } catch (Exception e) {
      log.error("쿠폰 발급 처리 실패 - CouponId: {}, UserId: {}", couponId, userId, e);
      // 실패 시 수량 복구
      String quantityKey = "coupon:quantity:" + couponId;
      redisTemplate.opsForValue().increment(quantityKey);
      return false;
    }
  }
}
