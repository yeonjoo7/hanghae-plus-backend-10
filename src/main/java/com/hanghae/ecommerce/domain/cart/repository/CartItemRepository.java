package com.hanghae.ecommerce.domain.cart.repository;

import com.hanghae.ecommerce.domain.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 아이템 Repository - Spring Data JPA
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * 장바구니 ID로 아이템 목록 조회
     */
    List<CartItem> findByCartId(Long cartId);

    /**
     * 장바구니 ID와 상품 ID로 아이템 조회
     */
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /**
     * 장바구니 ID와 상품 ID, 옵션 ID로 아이템 조회
     */
    Optional<CartItem> findByCartIdAndProductIdAndProductOptionId(Long cartId, Long productId, Long productOptionId);

    /**
     * 상품 ID로 아이템 목록 조회
     */
    List<CartItem> findByProductId(Long productId);

    /**
     * 장바구니 ID와 상태로 아이템 목록 조회
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cartId = :cartId AND ci.state = :state")
    List<CartItem> findByCartIdAndState(@Param("cartId") Long cartId,
            @Param("state") com.hanghae.ecommerce.domain.cart.CartState state);

    /**
     * 장바구니 ID, 상품 ID, 옵션 없음, 상태로 아이템 조회
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cartId = :cartId AND ci.productId = :productId AND ci.productOptionId IS NULL AND ci.state = :state")
    Optional<CartItem> findByCartIdAndProductIdAndProductOptionIdIsNullAndState(
            @Param("cartId") Long cartId,
            @Param("productId") Long productId,
            @Param("state") com.hanghae.ecommerce.domain.cart.CartState state);

    /**
     * ID 목록, 장바구니 ID, 상태로 아이템 목록 조회
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.id IN :ids AND ci.cartId = :cartId AND ci.state = :state")
    List<CartItem> findByIdInAndCartIdAndState(
            @Param("ids") List<Long> ids,
            @Param("cartId") Long cartId,
            @Param("state") com.hanghae.ecommerce.domain.cart.CartState state);

    /**
     * ID, 장바구니 ID, 상태로 아이템 조회
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.id = :id AND ci.cartId = :cartId AND ci.state = :state")
    Optional<CartItem> findByIdAndCartIdAndState(
            @Param("id") Long id,
            @Param("cartId") Long cartId,
            @Param("state") com.hanghae.ecommerce.domain.cart.CartState state);

    /**
     * 장바구니 ID로 모든 아이템 삭제
     */
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cartId = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);

    /**
     * 장바구니 ID와 상품 ID로 아이템 삭제
     */
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cartId = :cartId AND ci.productId = :productId")
    void deleteByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);

    /**
     * 장바구니 ID별 아이템 수 조회
     */
    long countByCartId(Long cartId);

    /**
     * 상품 ID별 아이템 수 조회
     */
    long countByProductId(Long productId);
}