package com.hanghae.ecommerce.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * 수량을 나타내는 Value Object
 */
@Embeddable
public class Quantity {
    @Column(name = "value")
    private int value;

    // JPA를 위한 기본 생성자
    protected Quantity() {
    }

    private Quantity(int value) {
        validateQuantity(value);
        this.value = value;
    }

    /**
     * 수량 생성
     */
    public static Quantity of(int value) {
        return new Quantity(value);
    }

    /**
     * 0개 생성
     */
    public static Quantity zero() {
        return new Quantity(0);
    }

    /**
     * 수량 검증
     */
    private void validateQuantity(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("수량은 0 이상이어야 합니다: " + value);
        }
    }

    /**
     * 수량 추가
     */
    public Quantity add(Quantity other) {
        if (other == null) {
            throw new IllegalArgumentException("추가할 수량은 null일 수 없습니다.");
        }
        return new Quantity(this.value + other.value);
    }

    /**
     * 수량 차감
     */
    public Quantity subtract(Quantity other) {
        if (other == null) {
            throw new IllegalArgumentException("차감할 수량은 null일 수 없습니다.");
        }
        int result = this.value - other.value;
        if (result < 0) {
            throw new IllegalArgumentException("수량이 부족합니다. 현재: " + this.value + ", 차감: " + other.value);
        }
        return new Quantity(result);
    }

    /**
     * 수량 비교 (이상)
     */
    public boolean isGreaterThanOrEqual(Quantity other) {
        if (other == null) {
            throw new IllegalArgumentException("비교할 수량은 null일 수 없습니다.");
        }
        return this.value >= other.value;
    }

    /**
     * 수량 비교 (초과)
     */
    public boolean isGreaterThan(Quantity other) {
        if (other == null) {
            throw new IllegalArgumentException("비교할 수량은 null일 수 없습니다.");
        }
        return this.value > other.value;
    }

    /**
     * 0인지 확인
     */
    public boolean isZero() {
        return this.value == 0;
    }

    /**
     * 양수인지 확인
     */
    public boolean isPositive() {
        return this.value > 0;
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Quantity quantity = (Quantity) o;
        return value == quantity.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}