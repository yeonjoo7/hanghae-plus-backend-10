package com.hanghae.ecommerce.application.coupon;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponInfo;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.hanghae.ecommerce.presentation.exception.CouponAlreadyIssuedException;
import com.hanghae.ecommerce.presentation.exception.CouponNotFoundException;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.infrastructure.coupon.CouponQueueService;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CouponService {

    private final JdbcTemplate jdbcTemplate;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final LockManager lockManager;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    private final CouponQueueService couponQueueService;

    public CouponService(JdbcTemplate jdbcTemplate,
            CouponRepository couponRepository,
            UserCouponRepository userCouponRepository,
            UserRepository userRepository,
            LockManager lockManager,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            CouponQueueService couponQueueService) {
        this.jdbcTemplate = jdbcTemplate;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
        this.lockManager = lockManager;
        this.transactionManager = transactionManager;
        this.couponQueueService = couponQueueService;
    }

    public UserCoupon issueCoupon(Long couponId, Long userId) {
        if (couponId == null || userId == null) {
            throw new IllegalArgumentException("쿠폰 ID와 사용자 ID는 null일 수 없습니다.");
        }

        // 0. 사용자 존재 확인 (락 획득 전)
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 1. 중복 발급 체크 (락 획득 전)
        var existingCoupons = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (!existingCoupons.isEmpty()) {
            throw new CouponAlreadyIssuedException();
        }

        String lockKey = "coupon:" + couponId;

        return lockManager.executeWithLock(lockKey, () -> {
            org.springframework.transaction.support.TransactionTemplate template = new org.springframework.transaction.support.TransactionTemplate(
                    transactionManager);
            template.setPropagationBehavior(
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            return template.execute(status -> {
                // 2. 중복 발급 재확인 (락 획득 후, Race Condition 방지)
                var recheck = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
                if (!recheck.isEmpty()) {
                    throw new CouponAlreadyIssuedException();
                }

                // 3. 쿠폰 정보 조회
                Coupon coupon = couponRepository.findById(couponId)
                        .orElseThrow(() -> new CouponNotFoundException(couponId));

                // 4. 쿠폰 수량 증가 (원자적 업데이트 with 수량 체크)
                int updatedRows = jdbcTemplate.update(
                        "UPDATE coupons SET issued_quantity = issued_quantity + 1 WHERE id = ? AND issued_quantity < total_quantity",
                        couponId);

                // 업데이트 실패 시 (다른 트랜잭션이 먼저 소진시킴)
                if (updatedRows == 0) {
                    throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
                }

                // 5. 사용자 쿠폰 발급
                LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

                UserCoupon userCoupon = UserCoupon.issue(
                        userId,
                        couponId,
                        expiresAt);

                return userCouponRepository.save(userCoupon);
            });
        });
    }

    public Coupon getCoupon(String couponId) {
        return couponRepository.findById(Long.valueOf(couponId))
                .orElseThrow(() -> new CouponNotFoundException(Long.valueOf(couponId)));
    }

    public List<Coupon> getAvailableCoupons() {
        // return couponRepository.findAvailableCoupons(LocalDateTime.now());
        return List.of();
    }

    public List<UserCouponInfo> getUserCoupons(Long userId) {
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);
        return userCoupons.stream()
                .map(userCoupon -> {
                    Coupon coupon = couponRepository.findById(userCoupon.getCouponId())
                            .orElseThrow(() -> new CouponNotFoundException(userCoupon.getCouponId()));
                    return new UserCouponInfo(userCoupon, coupon);
                })
                .collect(Collectors.toList());
    }

    public List<UserCoupon> getAvailableUserCoupons(Long userId) {
        return userCouponRepository.findByUserIdAndState(userId, UserCouponState.AVAILABLE);
    }

    @Transactional
    public void useCoupon(Long userCouponId, Long userId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new CouponNotFoundException(userCouponId));

        if (!userCoupon.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 쿠폰의 소유자가 아닙니다");
        }

        if (userCoupon.getState() != UserCouponState.AVAILABLE) {
            throw new IllegalStateException("사용 가능한 쿠폰이 아닙니다");
        }

        if (userCoupon.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("만료된 쿠폰입니다");
        }

        userCoupon.use();
        userCouponRepository.save(userCoupon);
    }

    public boolean validateCoupon(String couponId, String userId, double orderAmount) {
        // 사용자가 해당 쿠폰을 보유하고 있는지 확인
        var userCoupons = userCouponRepository.findByUserIdAndCouponId(Long.valueOf(userId), Long.valueOf(couponId));
        if (userCoupons.isEmpty()) {
            return false;
        }

        var userCoupon = userCoupons.stream()
                .filter(uc -> uc.getState() == UserCouponState.AVAILABLE)
                .filter(uc -> uc.getExpiresAt().isAfter(LocalDateTime.now()))
                .findFirst()
                .orElse(null);

        if (userCoupon == null) {
            return false;
        }

        // 쿠폰 정보로 최소 주문 금액 확인
        var coupon = couponRepository.findById(Long.parseLong(couponId)).orElse(null);
        if (coupon == null) {
            return false;
        }

        return orderAmount >= coupon.getDiscountPolicy().getMinOrderAmount().getValue();
    }

    public UserCouponInfo getUserCoupon(Long userId, Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new CouponNotFoundException(userCouponId));

        if (!userCoupon.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 쿠폰의 소유자가 아닙니다");
        }

        Coupon coupon = couponRepository.findById(userCoupon.getCouponId())
                .orElseThrow(() -> new CouponNotFoundException(userCoupon.getCouponId()));

        return new UserCouponInfo(userCoupon, coupon);
    }

    public List<UserCouponInfo> getCouponUsageHistory(Long userId) {
        List<UserCoupon> usedCoupons = userCouponRepository.findByUserIdAndState(userId, UserCouponState.USED);
        return usedCoupons.stream()
                .map(userCoupon -> {
                    Coupon coupon = couponRepository.findById(userCoupon.getCouponId())
                            .orElseThrow(() -> new CouponNotFoundException(userCoupon.getCouponId()));
                    return new UserCouponInfo(userCoupon, coupon);
                })
                .collect(Collectors.toList());
    }

    /**
     * Redis 기반 비동기 쿠폰 발급 요청
     *
     * 사용자 요청을 Redis 대기열에 추가하고 즉시 응답합니다.
     * 실제 발급은 스케줄러가 비동기로 처리합니다.
     *
     * ## 변경 사항 (리뷰 반영)
     * - Redis quantity 관리 제거: DB를 단일 진실 공급원(Single Source of Truth)으로 사용
     * - 복잡도 감소: Redis ↔ DB 간 수량 동기화 로직 제거
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     * @return 대기열 순위 (1부터 시작, -1이면 이미 발급됨 또는 대기열에 이미 존재)
     */
    public long requestCouponIssue(Long couponId, Long userId) {
        if (couponId == null || userId == null) {
            throw new IllegalArgumentException("쿠폰 ID와 사용자 ID는 null일 수 없습니다.");
        }

        // 1. 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 2. 쿠폰 정보 조회 및 유효성 검증
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        // 쿠폰 상태 및 기간 확인
        if (!coupon.getState().isIssuable()) {
            throw new IllegalStateException("쿠폰을 발급할 수 없습니다. 상태: " + coupon.getState());
        }
        if (!coupon.isWithinValidPeriod()) {
            throw new IllegalStateException("쿠폰 발급 기간이 아닙니다.");
        }

        // 3. Redis 대기열에 추가 (쿠폰 종료일 전달하여 TTL 설정)
        // 수량 확인은 스케줄러에서 DB를 통해 처리
        long position = couponQueueService.enqueue(couponId, userId, coupon.getEndDate());

        // enqueue()는 이미 발급된 경우에만 -1을 반환하므로, -1이면 이미 발급된 경우
        if (position == -1) {
            throw new CouponAlreadyIssuedException();
        }

        return position;
    }

    /**
     * 스케줄러에서 호출하는 실제 쿠폰 발급 메서드
     * 
     * Redis 대기열에서 처리된 사용자에 대해 실제 DB에 쿠폰을 발급합니다.
     * 
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     * @return 발급된 UserCoupon
     */
    @Transactional
    public UserCoupon issueCouponFromQueue(Long couponId, Long userId) {
        if (couponId == null || userId == null) {
            throw new IllegalArgumentException("쿠폰 ID와 사용자 ID는 null일 수 없습니다.");
        }

        // 1. 중복 발급 체크
        var existingCoupons = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (!existingCoupons.isEmpty()) {
            throw new CouponAlreadyIssuedException();
        }

        // 2. 쿠폰 정보 조회
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

        if (!coupon.canIssue()) {
            throw new IllegalStateException("쿠폰을 발급할 수 없습니다. 상태: " + coupon.getState());
        }

        // 3. 쿠폰 수량 증가 (원자적 업데이트)
        int updatedRows = jdbcTemplate.update(
                "UPDATE coupons SET issued_quantity = issued_quantity + 1 WHERE id = ? AND issued_quantity < total_quantity",
                couponId);

        if (updatedRows == 0) {
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
        }

        // 4. 사용자 쿠폰 발급
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        UserCoupon userCoupon = UserCoupon.issue(userId, couponId, expiresAt);

        return userCouponRepository.save(userCoupon);
    }

    /**
     * 대기열 순위 조회
     * 
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     * @return 대기열 순위 (1부터 시작, 없으면 -1)
     */
    public long getQueueRank(Long couponId, Long userId) {
        return couponQueueService.getQueueRank(couponId, userId);
    }

    /**
     * 대기열 크기 조회
     * 
     * @param couponId 쿠폰 ID
     * @return 대기열에 있는 사용자 수
     */
    public long getQueueSize(Long couponId) {
        return couponQueueService.getQueueSize(couponId);
    }
}