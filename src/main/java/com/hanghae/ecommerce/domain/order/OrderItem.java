package com.hanghae.ecommerce.domain.order;

import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Quantity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 주문 아이템 도메인 엔티티
 */
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Column(name = "order_id", nullable = false)
    private final Long orderId;

    @Column(name = "product_id", nullable = false)
    private final Long productId;

    @Column(name = "product_option_id")
    private final Long productOptionId; // nullable

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private OrderState state;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    })
    private Money price; // 상품 단가

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    })
    private Quantity quantity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", nullable = false))
    })
    private Money discountAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    })
    private Money totalAmount; // 총 금액 (price * quantity - discountAmount)

    @Column(name = "created_at", nullable = false, updatable = false)
    private final LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected OrderItem() {
        this.id = null;
        this.orderId = null;
        this.productId = null;
        this.productOptionId = null;
        this.createdAt = null;
    }

    private OrderItem(Long id, Long orderId, Long productId, Long productOptionId,
            OrderState state, Money price, Quantity quantity, Money discountAmount,
            Money totalAmount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productOptionId = productOptionId;
        this.state = state;
        this.price = price;
        this.quantity = quantity;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 주문 아이템 생성 (상품용)
     */
    public static OrderItem createForProduct(Long orderId, Long productId, Money price,
            Quantity quantity, Money discountAmount) {
        validateOrderId(orderId);
        validateProductId(productId);
        validatePrice(price);
        validateQuantity(quantity);
        validateDiscountAmount(discountAmount);

        Money totalAmount = calculateTotalAmount(price, quantity, discountAmount);

        LocalDateTime now = LocalDateTime.now();
        return new OrderItem(
                null,
                orderId,
                productId,
                null,
                OrderState.PENDING_PAYMENT,
                price,
                quantity,
                discountAmount,
                totalAmount,
                now,
                now);
    }

    /**
     * 새로운 주문 아이템 생성 (상품 옵션용)
     */
    public static OrderItem createForProductOption(Long orderId, Long productId, Long productOptionId,
            Money price, Quantity quantity, Money discountAmount) {
        validateOrderId(orderId);
        validateProductId(productId);
        validateProductOptionId(productOptionId);
        validatePrice(price);
        validateQuantity(quantity);
        validateDiscountAmount(discountAmount);

        Money totalAmount = calculateTotalAmount(price, quantity, discountAmount);

        LocalDateTime now = LocalDateTime.now();
        return new OrderItem(
                null,
                orderId,
                productId,
                productOptionId,
                OrderState.PENDING_PAYMENT,
                price,
                quantity,
                discountAmount,
                totalAmount,
                now,
                now);
    }

    /**
     * 기존 주문 아이템 복원 (DB에서 조회)
     */
    public static OrderItem restore(Long id, Long orderId, Long productId, Long productOptionId,
            OrderState state, Money price, Quantity quantity,
            Money discountAmount, Money totalAmount,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("주문 아이템 ID는 null일 수 없습니다.");
        }
        validateOrderId(orderId);
        validateProductId(productId);
        validateState(state);
        validatePrice(price);
        validateQuantity(quantity);
        validateDiscountAmount(discountAmount);
        validateTotalAmount(totalAmount);

        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new OrderItem(id, orderId, productId, productOptionId, state, price,
                quantity, discountAmount, totalAmount, createdAt, updatedAt);
    }

    /**
     * 주문 아이템 취소
     */
    public void cancel() {
        if (!state.canBeCancelled()) {
            throw new IllegalStateException("취소할 수 없는 주문 아이템 상태입니다: " + state);
        }

        this.state = OrderState.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 아이템 삭제 (소프트 삭제)
     */
    public void delete() {
        this.state = OrderState.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 완료 처리
     */
    public void complete() {
        if (!state.canBePaid()) {
            throw new IllegalStateException("완료 처리할 수 없는 주문 아이템 상태입니다: " + state);
        }

        this.state = OrderState.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 할인 금액 적용
     */
    public void applyDiscount(Money discountAmount) {
        if (state != OrderState.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 상태에서만 할인을 적용할 수 있습니다.");
        }
        validateDiscountAmount(discountAmount);

        this.discountAmount = discountAmount;
        this.totalAmount = calculateTotalAmount(this.price, this.quantity, discountAmount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 옵션 아이템인지 확인
     */
    public boolean isProductOptionItem() {
        return productOptionId != null;
    }

    /**
     * 할인이 적용되어 있는지 확인
     */
    public boolean hasDiscount() {
        return discountAmount.isPositive();
    }

    /**
     * 단가 조회 (getPrice의 별칭)
     */
    public Money getUnitPrice() {
        return price;
    }

    /**
     * 소계 계산 (단가 × 수량, 할인 적용 전)
     */
    public Money getSubtotal() {
        return price.multiply(quantity.getValue());
    }

    /**
     * 총 금액 계산
     */
    private static Money calculateTotalAmount(Money price, Quantity quantity, Money discountAmount) {
        Money subtotal = price.multiply(quantity.getValue());
        Money result = subtotal.subtract(discountAmount);

        return result;
    }

    // 검증 메서드들
    private static void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다.");
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

    private static void validateState(OrderState state) {
        if (state == null) {
            throw new IllegalArgumentException("주문 아이템 상태는 필수입니다.");
        }
    }

    private static void validatePrice(Money price) {
        if (price == null) {
            throw new IllegalArgumentException("상품 단가는 필수입니다.");
        }
        if (!price.isPositive()) {
            throw new IllegalArgumentException("상품 단가는 0보다 커야 합니다.");
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

    private static void validateDiscountAmount(Money discountAmount) {
        if (discountAmount == null) {
            throw new IllegalArgumentException("할인 금액은 필수입니다.");
        }
    }

    private static void validateTotalAmount(Money totalAmount) {
        if (totalAmount == null) {
            throw new IllegalArgumentException("총 금액은 필수입니다.");
        }
    }

    // Getter 메서드들
    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getProductOptionId() {
        return productOptionId;
    }

    public OrderState getState() {
        return state;
    }

    public Money getPrice() {
        return price;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public Money getDiscountAmount() {
        return discountAmount;
    }

    public Money getTotalAmount() {
        return totalAmount;
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
        OrderItem orderItem = (OrderItem) o;
        return Objects.equals(id, orderItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", productId=" + productId +
                ", productOptionId=" + productOptionId +
                ", price=" + price +
                ", quantity=" + quantity +
                ", totalAmount=" + totalAmount +
                '}';
    }
}