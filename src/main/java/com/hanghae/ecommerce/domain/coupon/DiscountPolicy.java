package com.hanghae.ecommerce.domain.coupon;

import com.hanghae.ecommerce.domain.product.Money;
import jakarta.persistence.*;
import java.util.List;
import java.util.Objects;

/**
 * 할인 정책을 나타내는 Value Object
 */
@Embeddable
public class DiscountPolicy {

    public enum DiscountType {
        PERCENTAGE, AMOUNT
    }

    public enum CouponType {
        ORDER, CART_ITEM
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType; // 할인 타입 (PERCENTAGE, AMOUNT)

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "discount_value"))
    })
    private Money discountAmount; // 할인 금액 (할인율의 경우에도 여기에 저장)

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "min_order_amount"))
    })
    private Money minOrderAmount; // 최소 주문 금액

    @Transient
    private List<Long> applicableProductIds; // 적용 가능한 상품 ID 목록

    @Transient
    private CouponType type; // 쿠폰 타입 (현재 사용하지 않음)

    // JPA를 위한 기본 생성자
    protected DiscountPolicy() {
    }

    private DiscountPolicy(DiscountType discountType, Money discountAmount, Money minOrderAmount,
            List<Long> applicableProductIds, CouponType type) {
        this.discountType = discountType;
        this.discountAmount = discountAmount;
        this.minOrderAmount = minOrderAmount;
        this.applicableProductIds = applicableProductIds;
        this.type = type;
    }

    /**
     * 할인율 기반 정책 생성
     */
    public static DiscountPolicy rate(int discountRate) {
        validateDiscountRate(discountRate);
        return new DiscountPolicy(DiscountType.PERCENTAGE, Money.of(discountRate), null, null, CouponType.ORDER);
    }

    /**
     * 할인 금액 기반 정책 생성
     */
    public static DiscountPolicy amount(Money discountAmount) {
        validateDiscountAmount(discountAmount);
        return new DiscountPolicy(DiscountType.AMOUNT, discountAmount, null, null, CouponType.ORDER);
    }

    /**
     * 기존 할인 정책 복원
     */
    public static DiscountPolicy restore(DiscountType discountType, Money discountAmount) {
        if (discountType == null) {
            throw new IllegalArgumentException("할인 타입은 필수입니다.");
        }
        if (discountAmount == null) {
            throw new IllegalArgumentException("할인 값은 필수입니다.");
        }

        validateDiscountAmount(discountAmount);
        if (discountType == DiscountType.PERCENTAGE) {
            validateDiscountRate(discountAmount.getValue());
        }

        return new DiscountPolicy(discountType, discountAmount, null, null, CouponType.ORDER);
    }

    /**
     * 완전한 할인 정책 생성
     */
    public static DiscountPolicy of(DiscountType discountType, Money discountAmount, Money minOrderAmount,
            List<Long> applicableProductIds, CouponType type) {
        return new DiscountPolicy(discountType, discountAmount, minOrderAmount, applicableProductIds, type);
    }

    /**
     * 고정 할인 금액 정책 생성 (fixed 별칭)
     */
    public static DiscountPolicy fixed(Money discountAmount) {
        return amount(discountAmount);
    }

    /**
     * 할인율 정책 생성 (percentage 별칭)
     */
    public static DiscountPolicy percentage(int discountRate) {
        return rate(discountRate);
    }

    /**
     * 할인 금액 계산
     */
    public Money calculateDiscount(Money originalAmount) {
        if (originalAmount == null) {
            throw new IllegalArgumentException("원래 금액은 null일 수 없습니다.");
        }

        if (isRatePolicy()) {
            int discountValue = originalAmount.getValue() * discountAmount.getValue() / 100;
            return Money.of(discountValue);
        } else {
            // 할인 금액이 원래 금액보다 클 경우 원래 금액만큼만 할인
            return originalAmount.isGreaterThanOrEqual(discountAmount)
                    ? discountAmount
                    : originalAmount;
        }
    }

    /**
     * 할인율 기반 정책인지 확인
     */
    public boolean isRatePolicy() {
        return discountType == DiscountType.PERCENTAGE;
    }

    /**
     * 할인 금액 기반 정책인지 확인
     */
    public boolean isAmountPolicy() {
        return discountType == DiscountType.AMOUNT;
    }

    // 검증 메서드들
    private static void validateDiscountRate(int discountRate) {
        if (discountRate <= 0 || discountRate > 100) {
            throw new IllegalArgumentException("할인율은 1-100% 사이여야 합니다: " + discountRate);
        }
    }

    private static void validateDiscountAmount(Money discountAmount) {
        if (discountAmount == null) {
            throw new IllegalArgumentException("할인 금액은 null일 수 없습니다.");
        }
        if (!discountAmount.isPositive()) {
            throw new IllegalArgumentException("할인 금액은 0보다 커야 합니다.");
        }
    }

    // Getter 메서드들
    public DiscountType getDiscountType() {
        return discountType;
    }

    public Money getDiscountAmount() {
        return discountAmount;
    }

    public Money getMinOrderAmount() {
        return minOrderAmount;
    }

    public List<Long> getApplicableProductIds() {
        return applicableProductIds;
    }

    public CouponType getType() {
        return type;
    }


    /**
     * 할인 값 반환 (할인율 또는 할인 금액)
     */
    public int getDiscountValue() {
        return discountAmount.getValue();
    }

    /**
     * 최소 주문 금액 설정 여부 확인
     */
    public boolean hasMinOrderAmount() {
        return minOrderAmount != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DiscountPolicy that = (DiscountPolicy) o;
        return Objects.equals(discountType, that.discountType) &&
                Objects.equals(discountAmount, that.discountAmount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(discountType, discountAmount);
    }

    @Override
    public String toString() {
        if (isRatePolicy()) {
            return "할인율: " + discountAmount.getValue() + "%";
        } else {
            return "할인금액: " + discountAmount + "원";
        }
    }
}