package com.hanghae.ecommerce.domain.order;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * 주문 번호를 나타내는 Value Object
 */
public class OrderNumber {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final String value;

    private OrderNumber(String value) {
        validateOrderNumber(value);
        this.value = value;
    }

    /**
     * 새로운 주문 번호 생성
     */
    public static OrderNumber generate() {
        LocalDateTime now = LocalDateTime.now();
        String dateStr = now.format(FORMATTER);
        String uuid = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase();
        String orderNumber = "ORD" + dateStr + uuid;
        return new OrderNumber(orderNumber);
    }

    /**
     * 기존 주문 번호로부터 생성
     */
    public static OrderNumber of(String value) {
        return new OrderNumber(value);
    }

    /**
     * 주문 번호 검증
     */
    private void validateOrderNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("주문 번호는 필수입니다.");
        }
        if (value.length() > 50) {
            throw new IllegalArgumentException("주문 번호는 50자를 초과할 수 없습니다.");
        }
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderNumber that = (OrderNumber) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}