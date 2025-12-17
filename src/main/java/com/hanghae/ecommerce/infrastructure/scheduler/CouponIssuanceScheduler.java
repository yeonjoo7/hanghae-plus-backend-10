package com.hanghae.ecommerce.infrastructure.scheduler;

import com.hanghae.ecommerce.application.coupon.CouponService;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.infrastructure.coupon.CouponQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 스케줄러
 *
 * Redis 대기열에 있는 사용자들을 주기적으로 처리하여 실제 쿠폰을 발급합니다.
 *
 * ## 변경 사항 (리뷰 반영)
 * - Redis quantity 관리 제거: DB를 단일 진실 공급원(Single Source of Truth)으로 사용
 * - 복잡도 감소: Redis ↔ DB 간 수량 동기화 로직 제거
 *
 * ## 처리 방식
 * 1. 활성 쿠폰 목록 조회
 * 2. 각 쿠폰의 대기열에서 사용자를 순차적으로 꺼냄 (LPOP)
 * 3. DB에서 직접 수량 확인 후 발급 처리
 * 4. 발급 완료된 사용자는 발급 완료 Set에 추가
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.scheduler.coupon-issuance.enabled", havingValue = "true", matchIfMissing = false)
public class CouponIssuanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponIssuanceScheduler.class);

    private final CouponQueueService couponQueueService;
    private final CouponRepository couponRepository;
    private final CouponService couponService;

    // 배치 처리 크기 (한 번에 처리할 최대 인원)
    private static final int BATCH_SIZE = 100;

    public CouponIssuanceScheduler(
            CouponQueueService couponQueueService,
            CouponRepository couponRepository,
            CouponService couponService) {
        this.couponQueueService = couponQueueService;
        this.couponRepository = couponRepository;
        this.couponService = couponService;
    }

    /**
     * 쿠폰 발급 대기열 처리
     *
     * 1초마다 실행되어 대기열에 있는 사용자들을 처리합니다.
     */
    @Scheduled(fixedDelay = 1000)
    public void processCouponQueue() {
        try {
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
     */
    private void processCouponQueue(Coupon coupon) {
        Long couponId = coupon.getId();

        try {
            // 대기열이 비어있으면 건너뛰기
            long queueSize = couponQueueService.getQueueSize(couponId);
            if (queueSize == 0) {
                return;
            }

            int processCount = (int) Math.min(queueSize, BATCH_SIZE);
            int successCount = 0;

            for (int i = 0; i < processCount; i++) {
                // 대기열에서 한 명 꺼내기 (LPOP - 선착순)
                String userIdStr = couponQueueService.dequeue(couponId);
                if (userIdStr == null) {
                    break; // 대기열이 비었음
                }

                try {
                    Long userId = Long.valueOf(userIdStr);

                    // 이미 발급된 사용자는 건너뛰기
                    if (couponQueueService.isAlreadyIssued(couponId, userId)) {
                        continue;
                    }

                    // 실제 쿠폰 발급 처리 (DB에서 수량 확인 및 발급)
                    boolean issued = processCouponIssuance(couponId, userId);

                    if (issued) {
                        successCount++;
                        couponQueueService.markAsIssued(couponId, userId, coupon.getEndDate());
                        log.debug("쿠폰 발급 완료 - CouponId: {}, UserId: {}", couponId, userId);
                    } else {
                        // 발급 실패 (수량 부족) - 더 이상 처리하지 않음
                        log.info("쿠폰 수량 소진 - CouponId: {}", couponId);
                        break;
                    }
                } catch (Exception e) {
                    log.error("쿠폰 발급 처리 중 오류 - CouponId: {}, UserId: {}", couponId, userIdStr, e);
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
     * DB에서 직접 수량을 확인하고 발급합니다.
     * CouponService에서 동시성 제어 및 수량 관리를 담당합니다.
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     * @return 발급 성공 여부
     */
    private boolean processCouponIssuance(Long couponId, Long userId) {
        try {
            // CouponService에서 DB 기반으로 수량 확인 및 발급
            couponService.issueCouponFromQueue(couponId, userId);
            return true;
        } catch (IllegalStateException e) {
            // 수량 부족 또는 발급 불가능한 상태
            log.debug("쿠폰 발급 불가 - CouponId: {}, UserId: {}, 사유: {}", couponId, userId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("쿠폰 발급 처리 실패 - CouponId: {}, UserId: {}", couponId, userId, e);
            return false;
        }
    }
}
