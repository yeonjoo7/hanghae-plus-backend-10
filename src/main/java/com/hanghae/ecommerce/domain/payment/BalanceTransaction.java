package com.hanghae.ecommerce.domain.payment;

import com.hanghae.ecommerce.domain.user.Point;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 잔액 거래 도메인 엔티티
 */
public class BalanceTransaction {
    private final Long id;
    private final Long userId;
    private final Long orderId; // nullable
    private final TransactionType type;
    private final Point amount; // 거래 금액 (양수/음수)
    private final Point balanceBefore; // 거래 전 잔액
    private final Point balanceAfter; // 거래 후 잔액
    private String description;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private BalanceTransaction(Long id, Long userId, Long orderId, TransactionType type,
                              Point amount, Point balanceBefore, Point balanceAfter,
                              String description, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 포인트 충전 거래 생성
     */
    public static BalanceTransaction createCharge(Long userId, Point chargeAmount, Point currentBalance, String description) {
        validateUserId(userId);
        validateChargeAmount(chargeAmount);
        validateBalance(currentBalance);

        Point newBalance = currentBalance.add(chargeAmount);
        LocalDateTime now = LocalDateTime.now();

        return new BalanceTransaction(
            null,
            userId,
            null,
            TransactionType.CHARGE,
            chargeAmount,
            currentBalance,
            newBalance,
            description,
            now,
            now
        );
    }

    /**
     * 결제 거래 생성
     */
    public static BalanceTransaction createPayment(Long userId, Long orderId, Point paymentAmount, 
                                                  Point currentBalance, String description) {
        validateUserId(userId);
        validateOrderId(orderId);
        validatePaymentAmount(paymentAmount);
        validateBalance(currentBalance);

        if (!currentBalance.isGreaterThanOrEqual(paymentAmount)) {
            throw new IllegalArgumentException("잔액이 부족합니다. 현재: " + currentBalance.getValue() + 
                                             ", 결제: " + paymentAmount.getValue());
        }

        Point newBalance = currentBalance.subtract(paymentAmount);
        LocalDateTime now = LocalDateTime.now();

        return new BalanceTransaction(
            null,
            userId,
            orderId,
            TransactionType.PAYMENT,
            paymentAmount, // 양수로 저장, 타입으로 구분
            currentBalance,
            newBalance,
            description,
            now,
            now
        );
    }

    /**
     * 환불 거래 생성
     */
    public static BalanceTransaction createRefund(Long userId, Long orderId, Point refundAmount, 
                                                 Point currentBalance, String description) {
        validateUserId(userId);
        validateOrderId(orderId);
        validateRefundAmount(refundAmount);
        validateBalance(currentBalance);

        Point newBalance = currentBalance.add(refundAmount);
        LocalDateTime now = LocalDateTime.now();

        return new BalanceTransaction(
            null,
            userId,
            orderId,
            TransactionType.REFUND,
            refundAmount,
            currentBalance,
            newBalance,
            description,
            now,
            now
        );
    }

    /**
     * 기존 거래 복원 (DB에서 조회)
     */
    public static BalanceTransaction restore(Long id, Long userId, Long orderId, TransactionType type,
                                           Point amount, Point balanceBefore, Point balanceAfter,
                                           String description, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("거래 ID는 null일 수 없습니다.");
        }
        validateUserId(userId);
        validateType(type);
        validateAmount(amount);
        validateBalance(balanceBefore);
        validateBalance(balanceAfter);
        
        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new BalanceTransaction(id, userId, orderId, type, amount, balanceBefore,
                                    balanceAfter, description, createdAt, updatedAt);
    }

    /**
     * 거래 설명 수정
     */
    public void updateDescription(String description) {
        validateDescription(description);
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 잔액 증가 거래인지 확인
     */
    public boolean isBalanceIncrease() {
        return type.isBalanceIncrease();
    }

    /**
     * 잔액 감소 거래인지 확인
     */
    public boolean isBalanceDecrease() {
        return type.isBalanceDecrease();
    }

    /**
     * 주문과 연관된 거래인지 확인
     */
    public boolean isOrderRelated() {
        return orderId != null;
    }

    /**
     * 거래 금액의 절댓값 반환
     */
    public Point getAbsoluteAmount() {
        int absoluteValue = Math.abs(amount.getValue());
        return Point.of(absoluteValue);
    }

    // 검증 메서드들
    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
    }

    private static void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다.");
        }
    }

    private static void validateType(TransactionType type) {
        if (type == null) {
            throw new IllegalArgumentException("거래 유형은 필수입니다.");
        }
    }

    private static void validateChargeAmount(Point chargeAmount) {
        if (chargeAmount == null) {
            throw new IllegalArgumentException("충전 금액은 필수입니다.");
        }
        if (!chargeAmount.isGreaterThan(Point.zero())) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }
    }

    private static void validatePaymentAmount(Point paymentAmount) {
        if (paymentAmount == null) {
            throw new IllegalArgumentException("결제 금액은 필수입니다.");
        }
        if (!paymentAmount.isGreaterThan(Point.zero())) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
    }

    private static void validateRefundAmount(Point refundAmount) {
        if (refundAmount == null) {
            throw new IllegalArgumentException("환불 금액은 필수입니다.");
        }
        if (!refundAmount.isGreaterThan(Point.zero())) {
            throw new IllegalArgumentException("환불 금액은 0보다 커야 합니다.");
        }
    }

    private static void validateAmount(Point amount) {
        if (amount == null) {
            throw new IllegalArgumentException("거래 금액은 필수입니다.");
        }
    }

    private static void validateBalance(Point balance) {
        if (balance == null) {
            throw new IllegalArgumentException("잔액은 필수입니다.");
        }
    }

    private static void validateDescription(String description) {
        if (description != null && description.length() > 500) {
            throw new IllegalArgumentException("거래 설명은 500자를 초과할 수 없습니다.");
        }
    }

    // Getter 메서드들
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getOrderId() { return orderId; }
    public TransactionType getType() { return type; }
    public Point getAmount() { return amount; }
    public Point getBalanceBefore() { return balanceBefore; }
    public Point getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    
    /**
     * 거래 유형 반환 (getTransactionType 별칭)
     */
    public TransactionType getTransactionType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BalanceTransaction that = (BalanceTransaction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "BalanceTransaction{" +
                "id=" + id +
                ", userId=" + userId +
                ", orderId=" + orderId +
                ", type=" + type +
                ", amount=" + amount +
                ", balanceBefore=" + balanceBefore +
                ", balanceAfter=" + balanceAfter +
                '}';
    }
}