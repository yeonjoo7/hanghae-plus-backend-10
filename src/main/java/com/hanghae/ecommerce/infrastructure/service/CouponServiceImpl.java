package com.hanghae.ecommerce.infrastructure.service;

import com.hanghae.ecommerce.application.service.CouponService;
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
public class CouponServiceImpl implements CouponService {

    @Override
    public List<UserCoupon> getAvailableUserCoupons(String userId) {
        // TODO: 구현 필요
        return List.of();
    }

    private final JdbcTemplate jdbcTemplate;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    public CouponServiceImpl(JdbcTemplate jdbcTemplate,
            CouponRepository couponRepository,
            UserCouponRepository userCouponRepository,
            UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserCoupon issueCoupon(Long couponId, Long userId) {
        // 0. 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 1. 쿠폰 정보 조회 및 락
        List<Map<String, Object>> coupons = jdbcTemplate.queryForList(
                """
                        SELECT * FROM coupons
                        WHERE id = ?
                        AND issued_quantity < total_quantity
                        AND NOW() BETWEEN start_date AND end_date
                        FOR UPDATE
                        """,
                couponId);

        if (coupons.isEmpty()) {
            throw new CouponNotFoundException(Long.valueOf(couponId));
        }

        Map<String, Object> couponData = coupons.get(0);
        int totalQuantity = (Integer) couponData.get("total_quantity");
        int issuedQuantity = (Integer) couponData.get("issued_quantity");

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
        String userCouponId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        UserCoupon userCoupon = UserCoupon.issue(
                Long.valueOf(userId),
                Long.valueOf(couponId),
                expiresAt);

        userCouponRepository.save(userCoupon);

        // 남은 수량 정보는 도메인 객체에서 처리됩니다

        return userCoupon;
    }

    @Override
    public Coupon getCoupon(String couponId) {
        return couponRepository.findById(Long.valueOf(couponId))
                .orElseThrow(() -> new CouponNotFoundException(Long.valueOf(couponId)));
    }

    @Override
    public List<Coupon> getAvailableCoupons() {
        return couponRepository.findAvailableCoupons(LocalDateTime.now());
    }

    @Override
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

    @Override
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

    @Override
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
        var coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            return false;
        }

        return orderAmount >= coupon.getDiscountPolicy().getMinOrderAmount().getValue();
    }

    @Override
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

    @Override
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