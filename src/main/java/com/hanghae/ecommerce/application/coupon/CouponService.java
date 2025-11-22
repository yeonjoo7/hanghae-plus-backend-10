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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CouponService {

    private final JdbcTemplate jdbcTemplate;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    public CouponService(JdbcTemplate jdbcTemplate,
            CouponRepository couponRepository,
            UserCouponRepository userCouponRepository,
            UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UserCoupon issueCoupon(Long couponId, Long userId) {
        // 0. 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 1. 쿠폰 정보 조회 및 락 (수량 체크 제거 - 락 획득 후 체크)
        List<Map<String, Object>> coupons = jdbcTemplate.queryForList(
                """
                        SELECT * FROM coupons
                        WHERE id = ?
                        FOR UPDATE
                        """,
                couponId);

        if (coupons.isEmpty()) {
            throw new CouponNotFoundException(Long.valueOf(couponId));
        }

        Map<String, Object> couponData = coupons.get(0);
        int totalQuantity = (Integer) couponData.get("total_quantity");
        int issuedQuantity = (Integer) couponData.get("issued_quantity");

        // 1-1. 수량 체크 (락 획득 후)
        if (issuedQuantity >= totalQuantity) {
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
        }

        // 2. 중복 발급 체크
        var existingCoupons = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (!existingCoupons.isEmpty()) {
            throw new CouponAlreadyIssuedException();
        }

        // 3. 쿠폰 수량 증가
        jdbcTemplate.update(
                "UPDATE coupons SET issued_quantity = issued_quantity + 1 WHERE id = ?",
                couponId);

        // 4. 사용자 쿠폰 발급
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        UserCoupon userCoupon = UserCoupon.issue(
                Long.valueOf(userId),
                Long.valueOf(couponId),
                expiresAt);

        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        // 남은 수량 정보는 도메인 객체에서 처리됩니다

        return savedUserCoupon;
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
}