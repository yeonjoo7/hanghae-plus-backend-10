package com.hanghae.ecommerce.domain.cart.repository;

import com.hanghae.ecommerce.domain.cart.CartItem;
import com.hanghae.ecommerce.domain.cart.CartState;
import java.util.List;
import java.util.Optional;

/**
 * 장바구니 아이템 Repository 인터페이스
 */
public interface CartItemRepository {
    
    /**
     * 장바구니 아이템 저장
     */
    CartItem save(CartItem cartItem);
    
    /**
     * ID로 장바구니 아이템 조회
     */
    Optional<CartItem> findById(Long id);
    
    /**
     * 장바구니 ID로 아이템 목록 조회
     */
    List<CartItem> findByCartId(Long cartId);
    
    /**
     * 장바구니 ID와 상태로 아이템 목록 조회
     */
    List<CartItem> findByCartIdAndState(Long cartId, CartState state);
    
    /**
     * 활성 상태 장바구니 아이템 목록 조회 (장바구니별)
     */
    List<CartItem> findActiveItemsByCartId(Long cartId);
    
    /**
     * 상품 ID로 장바구니 아이템 목록 조회
     */
    List<CartItem> findByProductId(Long productId);
    
    /**
     * 상품 옵션 ID로 장바구니 아이템 목록 조회
     */
    List<CartItem> findByProductIdAndProductOptionId(Long productId, Long productOptionId);
    
    /**
     * 특정 장바구니에서 동일 상품/옵션 아이템 조회
     */
    Optional<CartItem> findByCartIdAndProductIdAndProductOptionId(Long cartId, Long productId, Long productOptionId);
    
    /**
     * 상태별 장바구니 아이템 목록 조회
     */
    List<CartItem> findByState(CartState state);
    
    /**
     * 모든 장바구니 아이템 조회
     */
    List<CartItem> findAll();
    
    /**
     * 장바구니 아이템 존재 여부 확인
     */
    boolean existsById(Long id);
    
    /**
     * 장바구니에 아이템 존재 여부 확인
     */
    boolean existsByCartId(Long cartId);
    
    /**
     * 특정 상품/옵션이 장바구니에 있는지 확인
     */
    boolean existsByCartIdAndProductIdAndProductOptionId(Long cartId, Long productId, Long productOptionId);
    
    /**
     * 장바구니 아이템 삭제
     */
    void deleteById(Long id);
    
    /**
     * 특정 장바구니의 모든 아이템 삭제
     */
    void deleteByCartId(Long cartId);
    
    /**
     * 특정 상품의 모든 장바구니 아이템 삭제
     */
    void deleteByProductId(Long productId);
    
    /**
     * 전체 장바구니 아이템 수 조회
     */
    long count();
    
    /**
     * 장바구니별 아이템 수 조회
     */
    long countByCartId(Long cartId);
    
    /**
     * 활성 아이템 수 조회 (장바구니별)
     */
    long countActiveItemsByCartId(Long cartId);
    
    /**
     * 상태별 장바구니 아이템 수 조회
     */
    long countByState(CartState state);
    
    /**
     * 장바구니에서 특정 상품(옵션 없음)과 상태로 아이템 조회
     */
    Optional<CartItem> findByCartIdAndProductIdAndProductOptionIdIsNullAndState(Long cartId, Long productId, CartState state);
    
    /**
     * 여러 아이템을 한번에 저장
     */
    List<CartItem> saveAll(List<CartItem> cartItems);
    
    /**
     * 여러 ID와 장바구니 ID, 상태로 아이템 목록 조회
     */
    List<CartItem> findByIdInAndCartIdAndState(List<Long> ids, Long cartId, CartState state);
    
    /**
     * ID와 장바구니 ID, 상태로 아이템 조회
     */
    Optional<CartItem> findByIdAndCartIdAndState(Long id, Long cartId, CartState state);
}