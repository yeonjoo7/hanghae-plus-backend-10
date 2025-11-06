package com.hanghae.ecommerce.domain.coupon.repository;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.CouponState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 Repository 인터페이스
 */
public interface CouponRepository {
    
    /**
     * 쿠폰 저장
     */
    Coupon save(Coupon coupon);
    
    /**
     * ID로 쿠폰 조회
     */
    Optional<Coupon> findById(Long id);
    
    /**
     * 쿠폰명으로 쿠폰 목록 조회 (부분 일치)
     */
    List<Coupon> findByNameContaining(String name);
    
    /**
     * 상태별 쿠폰 목록 조회
     */
    List<Coupon> findByState(CouponState state);
    
    /**
     * 발급 가능한 쿠폰 목록 조회
     */
    List<Coupon> findIssuableCoupons();
    
    /**
     * 유효 기간 내 쿠폰 목록 조회
     */
    List<Coupon> findValidCoupons(LocalDateTime now);
    
    /**
     * 만료된 쿠폰 목록 조회
     */
    List<Coupon> findExpiredCoupons(LocalDateTime now);
    
    /**
     * 잔여 수량이 있는 쿠폰 목록 조회
     */
    List<Coupon> findCouponsWithRemainingQuantity();
    
    /**
     * 모든 쿠폰 조회
     */
    List<Coupon> findAll();
    
    /**
     * 쿠폰 존재 여부 확인
     */
    boolean existsById(Long id);
    
    /**
     * 쿠폰명 존재 여부 확인
     */
    boolean existsByName(String name);
    
    /**
     * 쿠폰 삭제
     */
    void deleteById(Long id);
    
    /**
     * 전체 쿠폰 수 조회
     */
    long count();
    
    /**
     * 상태별 쿠폰 수 조회
     */
    long countByState(CouponState state);
    
    /**
     * ID로 쿠폰 조회 (업데이트용 - 락 적용)
     */
    Optional<Coupon> findByIdForUpdate(Long id);
}