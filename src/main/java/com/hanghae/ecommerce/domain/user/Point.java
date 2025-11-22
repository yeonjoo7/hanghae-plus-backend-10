package com.hanghae.ecommerce.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * 포인트를 나타내는 Value Object
 */
@Embeddable
public class Point {
    @Column(name = "value")
    private final int value;

    private Point(int value) {
        validatePoint(value);
        this.value = value;
    }

    /**
     * 포인트 생성
     */
    public static Point of(int value) {
        return new Point(value);
    }

    /**
     * 0 포인트 생성
     */
    public static Point zero() {
        return new Point(0);
    }

    /**
     * 포인트 값 검증
     */
    private void validatePoint(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("포인트는 0 이상이어야 합니다: " + value);
        }
    }

    /**
     * 포인트 추가
     */
    public Point add(Point other) {
        if (other == null) {
            throw new IllegalArgumentException("추가할 포인트는 null일 수 없습니다.");
        }
        return new Point(this.value + other.value);
    }

    /**
     * 포인트 차감
     */
    public Point subtract(Point other) {
        if (other == null) {
            throw new IllegalArgumentException("차감할 포인트는 null일 수 없습니다.");
        }
        int result = this.value - other.value;
        if (result < 0) {
            throw new IllegalArgumentException("포인트가 부족합니다. 현재: " + this.value + ", 차감: " + other.value);
        }
        return new Point(result);
    }

    /**
     * 포인트 비교 (이상)
     */
    public boolean isGreaterThanOrEqual(Point other) {
        if (other == null) {
            throw new IllegalArgumentException("비교할 포인트는 null일 수 없습니다.");
        }
        return this.value >= other.value;
    }

    /**
     * 포인트 비교 (초과)
     */
    public boolean isGreaterThan(Point other) {
        if (other == null) {
            throw new IllegalArgumentException("비교할 포인트는 null일 수 없습니다.");
        }
        return this.value > other.value;
    }

    /**
     * 0인지 확인
     */
    public boolean isZero() {
        return this.value == 0;
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
        Point point = (Point) o;
        return value == point.value;
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