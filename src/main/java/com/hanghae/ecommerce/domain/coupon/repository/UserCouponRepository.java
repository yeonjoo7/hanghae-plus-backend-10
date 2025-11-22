package com.hanghae.ecommerce.domain.coupon.repository;

import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 쿠폰 Repository - Spring Data JPA
 */
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

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
    /*
     * @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.state = com.hanghae.ecommerce.domain.coupon.UserCouponState.AVAILABLE ORDER BY uc.issuedAt DESC"
     * )
     * List<UserCoupon> findAvailableCouponsByUserId(@Param("userId") Long userId);
     * 
     * @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.couponId = :couponId AND uc.state = com.hanghae.ecommerce.domain.coupon.UserCouponState.AVAILABLE"
     * )
     * Optional<UserCoupon> findAvailableCoupon(@Param("userId") Long
     * userId, @Param("couponId") Long couponId);
     * 
     * @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId ORDER BY uc.issuedAt DESC"
     * )
     * List<UserCoupon> findByUserIdOrderByIssuedAtDesc(@Param("userId") Long
     * userId);
     * 
     * @Query("SELECT COUNT(uc) > 0 FROM UserCoupon uc WHERE uc.userId = :userId AND uc.couponId = :couponId"
     * )
     * boolean existsByUserIdAndCouponId(@Param("userId") Long
     * userId, @Param("couponId") Long couponId);
     */
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.state = com.hanghae.ecommerce.domain.coupon.UserCouponState.AVAILABLE AND :now BETWEEN uc.issuedAt AND uc.expiresAt ORDER BY uc.expiresAt ASC")
    List<UserCoupon> findAvailableCouponsByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 사용자의 만료된 쿠폰 목록 조회
     */
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.expiresAt < :now ORDER BY uc.expiresAt DESC")
    List<UserCoupon> findExpiredCouponsByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 만료 예정 쿠폰 목록 조회 (특정 일수 이내)
     */
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.state = com.hanghae.ecommerce.domain.coupon.UserCouponState.AVAILABLE AND uc.expiresAt < :expiryThreshold ORDER BY uc.expiresAt ASC")
    List<UserCoupon> findExpiringCoupons(@Param("expiryThreshold") LocalDateTime expiryThreshold);

    /**
     * 상태별 사용자 쿠폰 목록 조회
     */
    List<UserCoupon> findByState(UserCouponState state);

    /**
     * 사용자가 특정 쿠폰을 보유하고 있는지 확인
     */
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    /**
     * 특정 사용자의 모든 쿠폰 삭제
     */
    @Modifying
    @Query("DELETE FROM UserCoupon uc WHERE uc.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 특정 쿠폰의 모든 발급 내역 삭제
     */
    @Modifying
    @Query("DELETE FROM UserCoupon uc WHERE uc.couponId = :couponId")
    void deleteByCouponId(@Param("couponId") Long couponId);

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
     * 상태와 쿠폰 ID로 사용자 쿠폰 수 조회
     */
    int countByStateAndCouponId(UserCouponState state, Long couponId);

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
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.state = com.hanghae.ecommerce.domain.coupon.UserCouponState.AVAILABLE AND uc.expiresAt < :now")
    List<UserCoupon> findExpiredAvailableCoupons(@Param("now") LocalDateTime now);

    // Default method for backward compatibility
    default List<UserCoupon> findAvailableCouponsByUserId(Long userId) {
        return findAvailableCouponsByUserId(userId, LocalDateTime.now());
    }
}