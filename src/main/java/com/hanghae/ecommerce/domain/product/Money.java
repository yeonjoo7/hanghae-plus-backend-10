package com.hanghae.ecommerce.domain.product;

import java.util.Objects;

/**
 * 금액을 나타내는 Value Object
 */
public class Money {
    private final int amount;

    private Money(int amount) {
        validateAmount(amount);
        this.amount = amount;
    }

    /**
     * 금액 생성
     */
    public static Money of(int amount) {
        return new Money(amount);
    }

    /**
     * 0원 생성
     */
    public static Money zero() {
        return new Money(0);
    }

    /**
     * 금액 검증
     */
    private void validateAmount(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다: " + amount);
        }
    }

    /**
     * 금액 추가
     */
    public Money add(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("추가할 금액은 null일 수 없습니다.");
        }
        return new Money(this.amount + other.amount);
    }

    /**
     * 금액 곱셈
     */
    public Money multiply(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("곱셈 인수는 0 이상이어야 합니다: " + multiplier);
        }
        return new Money(this.amount * multiplier);
    }

    /**
     * 금액 비교 (이상)
     */
    public boolean isGreaterThanOrEqual(Money other) {
        if (other == null) {
            return true;
        }
        return this.amount >= other.amount;
    }

    /**
     * 0인지 확인
     */
    public boolean isZero() {
        return this.amount == 0;
    }

    /**
     * 양수인지 확인
     */
    public boolean isPositive() {
        return this.amount > 0;
    }

    public int getAmount() {
        return amount;
    }
    
    /**
     * 금액 값 반환 (getValue 별칭)
     */
    public int getValue() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount == money.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return String.valueOf(amount);
    }
}