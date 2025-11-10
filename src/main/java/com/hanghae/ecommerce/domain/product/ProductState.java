package com.hanghae.ecommerce.domain.product;

/**
 * 상품 상태를 나타내는 Value Object
 */
public enum ProductState {
    NORMAL("정상"),
    OUT_OF_STOCK("품절"),
    DISCONTINUED("단종"),
    DELETED("삭제됨");

    private final String description;

    ProductState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 판매 가능한 상태인지 확인
     */
    public boolean isAvailable() {
        return this == NORMAL;
    }

    /**
     * 삭제된 상태인지 확인
     */
    public boolean isDeleted() {
        return this == DELETED;
    }

    /**
     * 품절 상태인지 확인
     */
    public boolean isOutOfStock() {
        return this == OUT_OF_STOCK;
    }

    /**
     * 문자열로부터 ProductState 생성
     */
    public static ProductState fromString(String state) {
        if (state == null) {
            throw new IllegalArgumentException("상품 상태는 null일 수 없습니다.");
        }
        
        try {
            return ProductState.valueOf(state.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 상품 상태입니다: " + state);
        }
    }
}