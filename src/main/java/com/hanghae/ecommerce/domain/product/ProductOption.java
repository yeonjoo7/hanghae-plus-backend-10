package com.hanghae.ecommerce.domain.product;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 상품 옵션 도메인 엔티티
 */
public class ProductOption {
    private final Long id;
    private final Long productId;
    private ProductState state;
    private Money price;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ProductOption(Long id, Long productId, ProductState state, Money price, 
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.productId = productId;
        this.state = state;
        this.price = price;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 상품 옵션 생성
     */
    public static ProductOption create(Long productId, Money price) {
        validateProductId(productId);
        validatePrice(price);

        LocalDateTime now = LocalDateTime.now();
        return new ProductOption(
            null,
            productId,
            ProductState.NORMAL,
            price,
            now,
            now
        );
    }

    /**
     * 기존 상품 옵션 복원 (DB에서 조회)
     */
    public static ProductOption restore(Long id, Long productId, ProductState state, Money price,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("상품 옵션 ID는 null일 수 없습니다.");
        }
        validateProductId(productId);
        validatePrice(price);
        
        if (state == null) {
            throw new IllegalArgumentException("상품 옵션 상태는 null일 수 없습니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new ProductOption(id, productId, state, price, createdAt, updatedAt);
    }

    /**
     * 상품 옵션 가격 수정
     */
    public void updatePrice(Money price) {
        validatePrice(price);
        this.price = price;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 옵션 단종 처리
     */
    public void discontinue() {
        if (state == ProductState.DELETED) {
            throw new IllegalStateException("삭제된 상품 옵션은 단종 처리할 수 없습니다.");
        }
        this.state = ProductState.DISCONTINUED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 옵션 재활성화
     */
    public void activate() {
        if (state == ProductState.DELETED) {
            throw new IllegalStateException("삭제된 상품 옵션은 재활성화할 수 없습니다.");
        }
        this.state = ProductState.NORMAL;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 옵션 삭제 (소프트 삭제)
     */
    public void delete() {
        this.state = ProductState.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 사용 가능한 상태인지 확인
     */
    public boolean isAvailable() {
        return state.isAvailable();
    }

    // 검증 메서드들
    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
    }

    private static void validatePrice(Money price) {
        if (price == null) {
            throw new IllegalArgumentException("상품 옵션 가격은 필수입니다.");
        }
        if (!price.isPositive()) {
            throw new IllegalArgumentException("상품 옵션 가격은 0보다 커야 합니다.");
        }
    }

    // Getter 메서드들
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public ProductState getState() { return state; }
    public Money getPrice() { return price; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductOption that = (ProductOption) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProductOption{" +
                "id=" + id +
                ", productId=" + productId +
                ", state=" + state +
                ", price=" + price +
                '}';
    }
}