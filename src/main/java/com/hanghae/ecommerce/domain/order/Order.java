package com.hanghae.ecommerce.domain.order;

import com.hanghae.ecommerce.domain.product.Money;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 주문 도메인 엔티티
 */
public class Order {
    private final Long id;
    private final Long userId;
    private final Long userCouponId; // nullable
    private final Long cartId; // nullable
    private final OrderNumber orderNumber;
    private OrderState state;
    private Money amount; // 총 상품 금액
    private Money discountAmount; // 총 할인 금액
    private Money totalAmount; // 최종 결제 금액
    private Recipient recipient;
    private Address address;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Order(Long id, Long userId, Long userCouponId, Long cartId, OrderNumber orderNumber,
                 OrderState state, Money amount, Money discountAmount, Money totalAmount,
                 Recipient recipient, Address address, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.userCouponId = userCouponId;
        this.cartId = cartId;
        this.orderNumber = orderNumber;
        this.state = state;
        this.amount = amount;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.recipient = recipient;
        this.address = address;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 주문 생성
     */
    public static Order create(Long userId, Long userCouponId, Long cartId, Money amount,
                              Money discountAmount, Recipient recipient, Address address) {
        validateUserId(userId);
        validateAmount(amount);
        validateDiscountAmount(discountAmount);
        validateRecipient(recipient);
        validateAddress(address);

        Money totalAmount = amount.subtract(discountAmount);

        LocalDateTime now = LocalDateTime.now();
        return new Order(
            null,
            userId,
            userCouponId,
            cartId,
            OrderNumber.generate(),
            OrderState.PENDING_PAYMENT,
            amount,
            discountAmount,
            totalAmount,
            recipient,
            address,
            now,
            now
        );
    }

    /**
     * 기존 주문 복원 (DB에서 조회)
     */
    public static Order restore(Long id, Long userId, Long userCouponId, Long cartId,
                               OrderNumber orderNumber, OrderState state, Money amount,
                               Money discountAmount, Money totalAmount, Recipient recipient,
                               Address address, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("주문 ID는 null일 수 없습니다.");
        }
        validateUserId(userId);
        validateOrderNumber(orderNumber);
        validateState(state);
        validateAmount(amount);
        validateDiscountAmount(discountAmount);
        validateTotalAmount(totalAmount);
        validateRecipient(recipient);
        validateAddress(address);
        
        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new Order(id, userId, userCouponId, cartId, orderNumber, state, amount,
                        discountAmount, totalAmount, recipient, address, createdAt, updatedAt);
    }

    /**
     * 결제 완료 처리
     */
    public void completePayment() {
        if (!state.canBePaid()) {
            throw new IllegalStateException("결제할 수 없는 주문 상태입니다: " + state);
        }

        this.state = OrderState.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 취소
     */
    public void cancel() {
        if (!state.canBeCancelled()) {
            throw new IllegalStateException("취소할 수 없는 주문 상태입니다: " + state);
        }

        this.state = OrderState.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 환불
     */
    public void refund() {
        if (!state.canBeRefunded()) {
            throw new IllegalStateException("환불할 수 없는 주문 상태입니다: " + state);
        }

        this.state = OrderState.REFUNDED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 삭제 (소프트 삭제)
     */
    public void delete() {
        this.state = OrderState.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 배송지 정보 수정
     */
    public void updateDeliveryInfo(Recipient recipient, Address address) {
        if (state != OrderState.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 상태에서만 배송지 정보를 수정할 수 있습니다.");
        }
        validateRecipient(recipient);
        validateAddress(address);

        this.recipient = recipient;
        this.address = address;
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
        this.totalAmount = this.amount.subtract(discountAmount);

        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰이 적용되어 있는지 확인
     */
    public boolean hasCouponApplied() {
        return userCouponId != null;
    }

    /**
     * 할인이 적용되어 있는지 확인
     */
    public boolean hasDiscount() {
        return discountAmount.isPositive();
    }

    // 검증 메서드들
    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
    }

    private static void validateOrderNumber(OrderNumber orderNumber) {
        if (orderNumber == null) {
            throw new IllegalArgumentException("주문 번호는 필수입니다.");
        }
    }

    private static void validateState(OrderState state) {
        if (state == null) {
            throw new IllegalArgumentException("주문 상태는 필수입니다.");
        }
    }

    private static void validateAmount(Money amount) {
        if (amount == null) {
            throw new IllegalArgumentException("상품 금액은 필수입니다.");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("상품 금액은 0보다 커야 합니다.");
        }
    }

    private static void validateDiscountAmount(Money discountAmount) {
        if (discountAmount == null) {
            throw new IllegalArgumentException("할인 금액은 필수입니다.");
        }
    }

    private static void validateTotalAmount(Money totalAmount) {
        if (totalAmount == null) {
            throw new IllegalArgumentException("총 결제 금액은 필수입니다.");
        }
    }

    private static void validateRecipient(Recipient recipient) {
        if (recipient == null) {
            throw new IllegalArgumentException("수령인 정보는 필수입니다.");
        }
    }

    private static void validateAddress(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("배송지 정보는 필수입니다.");
        }
    }

    // Getter 메서드들
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getUserCouponId() { return userCouponId; }
    public Long getCartId() { return cartId; }
    public OrderNumber getOrderNumber() { return orderNumber; }
    public OrderState getState() { return state; }
    public Money getAmount() { return amount; }
    public Money getDiscountAmount() { return discountAmount; }
    public Money getTotalAmount() { return totalAmount; }
    public Recipient getRecipient() { return recipient; }
    public Address getAddress() { return address; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", orderNumber=" + orderNumber +
                ", state=" + state +
                ", totalAmount=" + totalAmount +
                ", recipient=" + recipient +
                '}';
    }
}