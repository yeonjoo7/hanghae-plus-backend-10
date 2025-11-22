package com.hanghae.ecommerce.domain.cart;

import com.hanghae.ecommerce.domain.product.Quantity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 장바구니 아이템 도메인 엔티티
 */
@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_option_id")
    private Long productOptionId; // nullable

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private CartState state;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    })
    private Quantity quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CartItem() {
        // JPA를 위한 기본 생성자
        this.id = null;
        this.cartId = null;
        this.productId = null;
        this.productOptionId = null;
        this.createdAt = LocalDateTime.now();
    }

    private CartItem(Long id, Long cartId, Long productId, Long productOptionId,
            CartState state, Quantity quantity, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.cartId = cartId;
        this.productId = productId;
        this.productOptionId = productOptionId;
        this.state = state;
        this.quantity = quantity;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 장바구니 아이템 생성 (상품용)
     */
    public static CartItem createForProduct(Long cartId, Long productId, Quantity quantity) {
        validateCartId(cartId);
        validateProductId(productId);
        validateQuantity(quantity);

        LocalDateTime now = LocalDateTime.now();
        return new CartItem(
                null,
                cartId,
                productId,
                null,
                CartState.NORMAL,
                quantity,
                now,
                now);
    }

    /**
     * 새로운 장바구니 아이템 생성 (상품 옵션용)
     */
    public static CartItem createForProductOption(Long cartId, Long productId, Long productOptionId,
            Quantity quantity) {
        validateCartId(cartId);
        validateProductId(productId);
        validateProductOptionId(productOptionId);
        validateQuantity(quantity);

        LocalDateTime now = LocalDateTime.now();
        return new CartItem(
                null,
                cartId,
                productId,
                productOptionId,
                CartState.NORMAL,
                quantity,
                now,
                now);
    }

    /**
     * 기존 장바구니 아이템 복원 (DB에서 조회)
     */
    public static CartItem restore(Long id, Long cartId, Long productId, Long productOptionId,
            CartState state, Quantity quantity, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("장바구니 아이템 ID는 null일 수 없습니다.");
        }
        validateCartId(cartId);
        validateProductId(productId);
        validateQuantity(quantity);

        if (state == null) {
            throw new IllegalArgumentException("장바구니 아이템 상태는 null일 수 없습니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new CartItem(id, cartId, productId, productOptionId, state, quantity, createdAt, updatedAt);
    }

    /**
     * 수량 변경
     */
    public void updateQuantity(Quantity newQuantity) {
        if (!isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 장바구니 아이템의 수량은 변경할 수 없습니다.");
        }
        validateQuantity(newQuantity);

        this.quantity = newQuantity;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 수량 증가
     */
    public void increaseQuantity(Quantity additionalQuantity) {
        if (!isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 장바구니 아이템의 수량은 증가시킬 수 없습니다.");
        }
        validateQuantity(additionalQuantity);
        if (additionalQuantity.isZero()) {
            throw new IllegalArgumentException("증가시킬 수량은 0보다 커야 합니다.");
        }

        this.quantity = this.quantity.add(additionalQuantity);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 수량 감소
     */
    public void decreaseQuantity(Quantity decreaseQuantity) {
        if (!isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 장바구니 아이템의 수량은 감소시킬 수 없습니다.");
        }
        validateQuantity(decreaseQuantity);
        if (decreaseQuantity.isZero()) {
            throw new IllegalArgumentException("감소시킬 수량은 0보다 커야 합니다.");
        }

        this.quantity = this.quantity.subtract(decreaseQuantity);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 장바구니 아이템 삭제 (소프트 삭제)
     */
    public void delete() {
        this.state = CartState.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 활성 상태인지 확인
     */
    public boolean isActive() {
        return state.isActive();
    }

    /**
     * 상품 옵션 아이템인지 확인
     */
    public boolean isProductOptionItem() {
        return productOptionId != null;
    }

    /**
     * 동일한 상품/옵션인지 확인
     */
    public boolean isSameProduct(Long productId, Long productOptionId) {
        return Objects.equals(this.productId, productId) &&
                Objects.equals(this.productOptionId, productOptionId);
    }

    // 검증 메서드들
    private static void validateCartId(Long cartId) {
        if (cartId == null) {
            throw new IllegalArgumentException("장바구니 ID는 필수입니다.");
        }
    }

    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
    }

    private static void validateProductOptionId(Long productOptionId) {
        if (productOptionId == null) {
            throw new IllegalArgumentException("상품 옵션 ID는 필수입니다.");
        }
    }

    private static void validateQuantity(Quantity quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("수량은 필수입니다.");
        }
        if (!quantity.isPositive()) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다.");
        }
    }

    // Getter 메서드들
    public Long getId() {
        return id;
    }

    public Long getCartId() {
        return cartId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getProductOptionId() {
        return productOptionId;
    }

    public CartState getState() {
        return state;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CartItem cartItem = (CartItem) o;
        return Objects.equals(id, cartItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CartItem{" +
                "id=" + id +
                ", cartId=" + cartId +
                ", productId=" + productId +
                ", productOptionId=" + productOptionId +
                ", quantity=" + quantity +
                ", state=" + state +
                '}';
    }
}