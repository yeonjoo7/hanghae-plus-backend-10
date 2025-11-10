package com.hanghae.ecommerce.domain.user;

/**
 * 사용자 상태를 나타내는 Value Object
 */
public enum UserState {
    NORMAL("정상"),
    INACTIVE("비활성"),
    DELETED("삭제됨");

    private final String description;

    UserState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 활성 상태인지 확인
     */
    public boolean isActive() {
        return this == NORMAL;
    }

    /**
     * 삭제된 상태인지 확인
     */
    public boolean isDeleted() {
        return this == DELETED;
    }

    /**
     * 문자열로부터 UserState 생성
     */
    public static UserState fromString(String state) {
        if (state == null) {
            throw new IllegalArgumentException("사용자 상태는 null일 수 없습니다.");
        }
        
        try {
            return UserState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 사용자 상태입니다: " + state);
        }
    }
}