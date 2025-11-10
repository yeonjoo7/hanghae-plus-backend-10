package com.hanghae.ecommerce.domain.cart.repository;

import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartState;
import java.util.List;
import java.util.Optional;

/**
 * 장바구니 Repository 인터페이스
 */
public interface CartRepository {
    
    /**
     * 장바구니 저장
     */
    Cart save(Cart cart);
    
    /**
     * ID로 장바구니 조회
     */
    Optional<Cart> findById(Long id);
    
    /**
     * 사용자 ID로 장바구니 조회
     */
    Optional<Cart> findByUserId(Long userId);
    
    /**
     * 사용자 ID와 상태로 장바구니 조회
     */
    Optional<Cart> findByUserIdAndState(Long userId, CartState state);
    
    /**
     * 활성 상태 장바구니 조회 (사용자별)
     */
    Optional<Cart> findActiveCartByUserId(Long userId);
    
    /**
     * 쿠폰이 적용된 장바구니 목록 조회
     */
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
     * 모든 장바구니 조회
     */
    List<Cart> findAll();
    
    /**
     * 장바구니 존재 여부 확인
     */
    boolean existsById(Long id);
    
    /**
     * 사용자 장바구니 존재 여부 확인
     */
    boolean existsByUserId(Long userId);
    
    /**
     * 활성 장바구니 존재 여부 확인 (사용자별)
     */
    boolean existsActiveCartByUserId(Long userId);
    
    /**
     * 장바구니 삭제
     */
    void deleteById(Long id);
    
    /**
     * 사용자의 모든 장바구니 삭제
     */
    void deleteByUserId(Long userId);
    
    /**
     * 전체 장바구니 수 조회
     */
    long count();
    
    /**
     * 사용자별 장바구니 수 조회
     */
    long countByUserId(Long userId);
    
    /**
     * 상태별 장바구니 수 조회
     */
    long countByState(CartState state);
}