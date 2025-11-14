package com.hanghae.ecommerce.infrastructure.service;

import com.hanghae.ecommerce.application.service.CouponService;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.hanghae.ecommerce.presentation.exception.CouponAlreadyIssuedException;
import com.hanghae.ecommerce.presentation.exception.CouponNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CouponServiceImpl implements CouponService {
    
    @Override
    public void useCoupon(String userId, String couponId) {
        // TODO: 구현 필요
        throw new UnsupportedOperationException("아직 구현되지 않았습니다");
    }
    
    @Override
    public List<UserCoupon> getAvailableUserCoupons(String userId) {
        // TODO: 구현 필요
        return List.of();
    }

    private final JdbcTemplate jdbcTemplate;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponServiceImpl(JdbcTemplate jdbcTemplate,
                           CouponRepository couponRepository,
                           UserCouponRepository userCouponRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
    }

    @Override
    @Transactional
    public UserCoupon issueCoupon(String couponId, String userId) {
        // 1. 쿠폰 정보 조회 및 락
        List<Map<String, Object>> coupons = jdbcTemplate.queryForList(
                """
                SELECT * FROM coupons
                WHERE id = ?
                AND issued_quantity < total_quantity
                AND NOW() BETWEEN start_date AND end_date
                FOR UPDATE
                """,
                couponId
        );

        if (coupons.isEmpty()) {
            throw new CouponNotFoundException(Long.valueOf(couponId));
        }

        Map<String, Object> couponData = coupons.get(0);
        int totalQuantity = (Integer) couponData.get("total_quantity");
        int issuedQuantity = (Integer) couponData.get("issued_quantity");

        // 2. 중복 발급 체크
        var existingCoupons = userCouponRepository.findByUserIdAndCouponId(Long.valueOf(userId), Long.valueOf(couponId));
        if (!existingCoupons.isEmpty()) {
            throw new CouponAlreadyIssuedException();
        }

        // 3. 쿠폰 수량 증가
        jdbcTemplate.update(
                "UPDATE coupons SET issued_quantity = issued_quantity + 1 WHERE id = ?",
                couponId
        );

        // 4. 사용자 쿠폰 발급
        String userCouponId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        
        UserCoupon userCoupon = UserCoupon.issue(
                Long.valueOf(userId),
                Long.valueOf(couponId),
                expiresAt
        );
        
        userCouponRepository.save(userCoupon);

        // 남은 수량 정보는 도메인 객체에서 처리됩니다
        
        return userCoupon;
    }

    @Override
    public Coupon getCoupon(String couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(Long.valueOf(couponId)));
    }

    @Override
    public List<Coupon> getAvailableCoupons() {
        return couponRepository.findAvailableCoupons(LocalDateTime.now());
    }

    @Override
    public List<UserCoupon> getUserCoupons(String userId) {
        return userCouponRepository.findByUserId(Long.valueOf(userId));
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
}