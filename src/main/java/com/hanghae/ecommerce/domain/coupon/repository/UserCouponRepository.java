package com.hanghae.ecommerce.domain.coupon.repository;

import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 쿠폰 Repository 인터페이스
 */
public interface UserCouponRepository {
    
    /**
     * 사용자 쿠폰 저장
     */
    UserCoupon save(UserCoupon userCoupon);
    
    /**
     * ID로 사용자 쿠폰 조회
     */
    Optional<UserCoupon> findById(Long id);
    
    /**
     * 사용자 ID로 쿠폰 목록 조회
     */
    List<UserCoupon> findByUserId(Long userId);
    
    /**
     * 사용자 ID와 쿠폰 ID로 쿠폰 조회
     */
    List<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);
    
    /**
     * 사용자 ID와 상태로 쿠폰 목록 조회
     */
    List<UserCoupon> findByUserIdAndState(Long userId, UserCouponState state);
    
    /**
     * 쿠폰 ID로 발급된 쿠폰 목록 조회
     */
    List<UserCoupon> findByCouponId(Long couponId);
    
    /**
     * 사용자의 사용 가능한 쿠폰 목록 조회
     */
    List<UserCoupon> findAvailableCouponsByUserId(Long userId);
    
    /**
     * 사용자의 만료된 쿠폰 목록 조회
     */
    List<UserCoupon> findExpiredCouponsByUserId(Long userId, LocalDateTime now);
    
    /**
     * 만료 예정 쿠폰 목록 조회 (특정 일수 이내)
     */
    List<UserCoupon> findExpiringCoupons(LocalDateTime expiryThreshold);
    
    /**
     * 상태별 사용자 쿠폰 목록 조회
     */
    List<UserCoupon> findByState(UserCouponState state);
    
    /**
     * 모든 사용자 쿠폰 조회
     */
    List<UserCoupon> findAll();
    
    /**
     * 사용자 쿠폰 존재 여부 확인
     */
    boolean existsById(Long id);
    
    /**
     * 사용자가 특정 쿠폰을 보유하고 있는지 확인
     */
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    
    /**
     * 사용자 쿠폰 삭제
     */
    void deleteById(Long id);
    
    /**
     * 특정 사용자의 모든 쿠폰 삭제
     */
    void deleteByUserId(Long userId);
    
    /**
     * 특정 쿠폰의 모든 발급 내역 삭제
     */
    void deleteByCouponId(Long couponId);
    
    /**
     * 전체 사용자 쿠폰 수 조회
     */
    long count();
    
    /**
     * 사용자별 쿠폰 수 조회
     */
    long countByUserId(Long userId);
    
    /**
     * 쿠폰별 발급 수량 조회
     */
    long countByCouponId(Long couponId);
    
    /**
     * 상태별 사용자 쿠폰 수 조회
     */
    long countByState(UserCouponState state);
    
    /**
     * 사용자 ID로 쿠폰 목록 조회 (발급일시 내림차순)
     */
    List<UserCoupon> findByUserIdOrderByIssuedAtDesc(Long userId);
    
    /**
     * 사용자 ID와 상태로 쿠폰 목록 조회 (발급일시 내림차순)
     */
    List<UserCoupon> findByUserIdAndStateOrderByIssuedAtDesc(Long userId, UserCouponState state);
    
    /**
     * 사용자 ID와 상태로 쿠폰 목록 조회 (사용일시 내림차순)
     */
    List<UserCoupon> findByUserIdAndStateOrderByUsedAtDesc(Long userId, UserCouponState state);
    
    /**
     * 만료된 사용 가능한 쿠폰 목록 조회
     */
    List<UserCoupon> findExpiredAvailableCoupons(LocalDateTime now);
    
    /**
     * 여러 사용자 쿠폰을 한번에 저장
     */
    List<UserCoupon> saveAll(List<UserCoupon> userCoupons);
    
    /**
     * 상태와 쿠폰 ID로 사용자 쿠폰 수 조회
     */
    int countByStateAndCouponId(UserCouponState state, Long couponId);
}