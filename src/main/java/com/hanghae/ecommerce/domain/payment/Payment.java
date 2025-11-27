package com.hanghae.ecommerce.domain.payment;

import com.hanghae.ecommerce.domain.product.Money;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 결제 도메인 엔티티
 */
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Column(name = "order_id", nullable = false)
    private final Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private PaymentState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private final PaymentMethod method;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
    })
    private Money paidAmount;

    @Column(name = "paid_at")
    private LocalDateTime paidAt; // 결제 완료 시점

    @Transient
    private LocalDateTime expiresAt; // nullable (현재 사용하지 않음)

    @Column(name = "created_at", nullable = false, updatable = false)
    private final LocalDateTime createdAt;

    @Transient
    private LocalDateTime updatedAt;

    protected Payment() {
        // JPA를 위한 기본 생성자
        this.id = null;
        this.orderId = null;
        this.method = null;
        this.createdAt = LocalDateTime.now();
    }

    private Payment(Long id, Long orderId, PaymentState state, PaymentMethod method,
            Money paidAmount, LocalDateTime paidAt, LocalDateTime expiresAt, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.state = state;
        this.method = method;
        this.paidAmount = paidAmount;
        this.paidAt = paidAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 결제 생성
     */
    public static Payment create(Long orderId, PaymentMethod method, Money paidAmount, LocalDateTime expiresAt) {
        validateOrderId(orderId);
        validateMethod(method);
        validatePaidAmount(paidAmount);
        validateExpiresAt(expiresAt, method);

        LocalDateTime now = LocalDateTime.now();
        return new Payment(
                null,
                orderId,
                PaymentState.PENDING,
                method,
                paidAmount,
                null,
                expiresAt,
                now,
                now);
    }

    /**
     * 기존 결제 복원 (DB에서 조회)
     */
    public static Payment restore(Long id, Long orderId, PaymentState state, PaymentMethod method,
            Money paidAmount, LocalDateTime paidAt, LocalDateTime expiresAt, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("결제 ID는 null일 수 없습니다.");
        }
        validateOrderId(orderId);
        validateState(state);
        validateMethod(method);
        validatePaidAmount(paidAmount);

        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new Payment(id, orderId, state, method, paidAmount, paidAt, expiresAt, createdAt, updatedAt);
    }

    /**
     * 결제 완료 처리
     */
    public void complete() {
        if (!state.canBeProcessed()) {
            throw new IllegalStateException("처리할 수 없는 결제 상태입니다: " + state);
        }
        if (isExpired()) {
            throw new IllegalStateException("만료된 결제는 완료 처리할 수 없습니다.");
        }

        this.state = PaymentState.COMPLETED;
        this.paidAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 결제 실패 처리
     */
    public void fail() {
        if (!state.canBeProcessed()) {
            throw new IllegalStateException("처리할 수 없는 결제 상태입니다: " + state);
        }

        this.state = PaymentState.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 결제 취소
     */
    public void cancel() {
        if (!state.canBeCancelled()) {
            throw new IllegalStateException("취소할 수 없는 결제 상태입니다: " + state);
        }

        this.state = PaymentState.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 결제 환불
     */
    public void refund() {
        if (!state.canBeRefunded()) {
            throw new IllegalStateException("환불할 수 없는 결제 상태입니다: " + state);
        }

        this.state = PaymentState.REFUNDED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 결제 삭제 (소프트 삭제)
     */
    public void delete() {
        this.state = PaymentState.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 결제 만료일 연장
     */
    public void extendExpiry(LocalDateTime newExpiresAt) {
        if (state != PaymentState.PENDING) {
            throw new IllegalStateException("대기 상태가 아닌 결제의 만료일은 연장할 수 없습니다.");
        }
        if (newExpiresAt == null) {
            throw new IllegalArgumentException("새로운 만료일시는 필수입니다.");
        }
        if (!newExpiresAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("새로운 만료일시는 현재 시간보다 이후여야 합니다.");
        }

        this.expiresAt = newExpiresAt;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 결제가 만료되었는지 확인
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 즉시 처리 가능한 결제인지 확인
     */
    public boolean isInstantProcessable() {
        return method.isInstantProcessable();
    }

    /**
     * 결제 처리 가능 여부 확인
     */
    public boolean canBeProcessed() {
        return state.canBeProcessed() && !isExpired();
    }

    // 검증 메서드들
    private static void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다.");
        }
    }

    private static void validateState(PaymentState state) {
        if (state == null) {
            throw new IllegalArgumentException("결제 상태는 필수입니다.");
        }
    }

    private static void validateMethod(PaymentMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("결제 수단은 필수입니다.");
        }
    }

    private static void validatePaidAmount(Money paidAmount) {
        if (paidAmount == null) {
            throw new IllegalArgumentException("결제 금액은 필수입니다.");
        }
        if (!paidAmount.isPositive()) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
    }

    private static void validateExpiresAt(LocalDateTime expiresAt, PaymentMethod method) {
        // 포인트 결제는 즉시 처리되므로 만료시간이 필요 없음
        if (method.isInstantProcessable()) {
            return;
        }

        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("결제 만료일시는 현재 시간보다 이후여야 합니다.");
        }
    }

    // Getter 메서드들
    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public PaymentState getState() {
        return state;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public Money getPaidAmount() {
        return paidAmount;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
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
        Payment payment = (Payment) o;
        return Objects.equals(id, payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Payment{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", state=" + state +
                ", method=" + method +
                ", paidAmount=" + paidAmount +
                '}';
    }
}