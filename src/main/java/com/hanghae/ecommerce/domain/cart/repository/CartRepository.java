package com.hanghae.ecommerce.domain.cart.repository;

import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 Repository - Spring Data JPA
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * 사용자 ID로 장바구니 조회
     */
    Optional<Cart> findByUserId(Long userId);

    /**
     * 사용자 ID와 상태로 장바구니 조회
     */
    Optional<Cart> findByUserIdAndState(Long userId, CartState state);

    /**
     * 사용자 ID와 상태로 모든 장바구니 조회 (최신순)
     * NonUniqueResultException 방지를 위해 List 반환
     */
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId AND c.state = :state ORDER BY c.createdAt DESC")
    List<Cart> findAllByUserIdAndState(@Param("userId") Long userId, @Param("state") CartState state);

    /**
     * 활성 상태 장바구니 조회 (사용자별)
     */
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId AND c.state = com.hanghae.ecommerce.domain.cart.CartState.NORMAL")
    Optional<Cart> findActiveCartByUserId(@Param("userId") Long userId);

    /**
     * 쿠폰이 적용된 장바구니 목록 조회
     */
    @Query("SELECT c FROM Cart c WHERE c.userCouponId IS NOT NULL")
    List<Cart> findCartsWithCoupon();

    /**
     * 특정 쿠폰이 적용된 장바구니 목록 조회
     */
    List<Cart> findByUserCouponId(Long userCouponId);

    /**
     * 상태별 장바구니 목록 조회
     */
    List<Cart> findByState(CartState state);

    /**
     * 사용자 장바구니 존재 여부 확인
     */
    boolean existsByUserId(Long userId);

    /**
     * 활성 장바구니 존재 여부 확인 (사용자별)
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Cart c WHERE c.userId = :userId AND c.state = com.hanghae.ecommerce.domain.cart.CartState.NORMAL")
    boolean existsActiveCartByUserId(@Param("userId") Long userId);

    /**
     * 사용자의 모든 장바구니 삭제
     */
    void deleteByUserId(Long userId);

    /**
     * 사용자별 장바구니 수 조회
     */
    long countByUserId(Long userId);

    /**
     * 상태별 장바구니 수 조회
     */
    long countByState(CartState state);
}