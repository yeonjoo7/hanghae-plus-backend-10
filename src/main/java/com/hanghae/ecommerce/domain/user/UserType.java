package com.hanghae.ecommerce.domain.user;

/**
 * 사용자 타입을 나타내는 Value Object
 */
public enum UserType {
    CUSTOMER("고객"),
    ADMIN("관리자");

    private final String description;

    UserType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 관리자인지 확인
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * 고객인지 확인
     */
    public boolean isCustomer() {
        return this == CUSTOMER;
    }

    /**
     * 문자열로부터 UserType 생성
     */
    public static UserType fromString(String type) {
        if (type == null) {
            throw new IllegalArgumentException("사용자 타입은 null일 수 없습니다.");
        }
        
        try {
            return UserType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 사용자 타입입니다: " + type);
        }
    }
}