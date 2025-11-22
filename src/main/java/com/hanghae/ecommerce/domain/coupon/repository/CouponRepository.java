package com.hanghae.ecommerce.domain.coupon.repository;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.CouponState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 Repository - Spring Data JPA
 * 
 * 기존 321줄의 JdbcTemplate 코드를 Spring Data JPA로 대체
 * - 비관적 락(Pessimistic Lock)을 @Lock 어노테이션으로 간단히 구현
 * - JPQL을 사용한 복잡한 쿼리 지원
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 이름으로 쿠폰 검색 (부분 일치)
     * Spring Data JPA가 자동으로 LIKE 쿼리 생성
     */
    List<Coupon> findByNameContaining(String name);

    /**
     * 이름 존재 여부 확인
     */
    boolean existsByName(String name);

    /**
     * 상태별 쿠폰 조회
     * 복잡한 조건이므로 JPQL 사용
     */
    @Query("SELECT c FROM Coupon c WHERE " +
            "CASE WHEN :state = 'NORMAL' THEN c.issuedQuantity.value < c.totalQuantity.value AND :now BETWEEN c.beginDate AND c.endDate "
            +
            "WHEN :state = 'DISCONTINUED' THEN c.issuedQuantity.value >= c.totalQuantity.value " +
            "WHEN :state = 'EXPIRED' THEN :now > c.endDate " +
            "ELSE true END " +
            "ORDER BY c.createdAt DESC")
    List<Coupon> findByState(@Param("state") CouponState state, @Param("now") LocalDateTime now);

    /**
     * 상태별 쿠폰 수 조회
     */
    @Query("SELECT COUNT(c) FROM Coupon c WHERE " +
            "CASE WHEN :state = 'NORMAL' THEN c.issuedQuantity.value < c.totalQuantity.value AND :now BETWEEN c.beginDate AND c.endDate "
            +
            "WHEN :state = 'DISCONTINUED' THEN c.issuedQuantity.value >= c.totalQuantity.value " +
            "WHEN :state = 'EXPIRED' THEN :now > c.endDate " +
            "ELSE true END")
    long countByState(@Param("state") CouponState state, @Param("now") LocalDateTime now);

    /**
     * 비관적 락으로 쿠폰 조회 (FOR UPDATE)
     * 동시성 제어를 위한 핵심 메서드
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    /**
     * 발급 가능한 쿠폰 조회
     * 수량이 남아있고 유효기간 내인 쿠폰
     */
    @Query("SELECT c FROM Coupon c WHERE " +
            "c.issuedQuantity.value < c.totalQuantity.value AND " +
            ":now BETWEEN c.beginDate AND c.endDate " +
            "ORDER BY c.endDate ASC")
    List<Coupon> findAvailableCoupons(@Param("now") LocalDateTime now);

    /**
     * 유효기간 내 쿠폰 조회
     */
    @Query("SELECT c FROM Coupon c WHERE :now BETWEEN c.beginDate AND c.endDate ORDER BY c.endDate ASC")
    List<Coupon> findValidCoupons(@Param("now") LocalDateTime now);

    /**
     * 만료된 쿠폰 조회
     */
    @Query("SELECT c FROM Coupon c WHERE :now > c.endDate ORDER BY c.endDate DESC")
    List<Coupon> findExpiredCoupons(@Param("now") LocalDateTime now);

    /**
     * 수량이 남아있는 쿠폰 조회
     */
    @Query("SELECT c FROM Coupon c WHERE c.issuedQuantity.value < c.totalQuantity.value ORDER BY c.createdAt DESC")
    List<Coupon> findCouponsWithRemainingQuantity();

    /**
     * 발급 가능한 쿠폰 목록 (별칭)
     */
    default List<Coupon> findIssuableCoupons() {
        return findAvailableCoupons(LocalDateTime.now());
    }

    // JpaRepository가 자동으로 제공하는 메서드들:
    // - Coupon save(Coupon coupon)
    // - Optional<Coupon> findById(Long id)
    // - List<Coupon> findAll()
    // - boolean existsById(Long id)
    // - void deleteById(Long id)
    // - long count()
}