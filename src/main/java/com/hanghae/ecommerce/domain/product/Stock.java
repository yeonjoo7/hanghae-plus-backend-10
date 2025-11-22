package com.hanghae.ecommerce.domain.product;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 재고 도메인 엔티티
 */
@Entity
@Table(name = "stocks")
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Column(name = "product_id", nullable = false)
    private final Long productId;

    @Column(name = "product_option_id")
    private final Long productOptionId; // nullable

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "available_quantity", nullable = false))
    })
    private Quantity availableQuantity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "sold_quantity", nullable = false))
    })
    private Quantity soldQuantity;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private final LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Stock(Long id, Long productId, Long productOptionId, Quantity availableQuantity,
            Quantity soldQuantity, String memo, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.productId = productId;
        this.productOptionId = productOptionId;
        this.availableQuantity = availableQuantity;
        this.soldQuantity = soldQuantity;
        this.memo = memo;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 재고 생성 (상품용)
     */
    public static Stock createForProduct(Long productId, Quantity initialQuantity, String memo) {
        validateProductId(productId);
        validateQuantity(initialQuantity);

        LocalDateTime now = LocalDateTime.now();
        return new Stock(
                null,
                productId,
                null,
                initialQuantity,
                Quantity.zero(),
                memo,
                now,
                now);
    }

    /**
     * 새로운 재고 생성 (상품 옵션용)
     */
    public static Stock createForProductOption(Long productId, Long productOptionId,
            Quantity initialQuantity, String memo) {
        validateProductId(productId);
        validateProductOptionId(productOptionId);
        validateQuantity(initialQuantity);

        LocalDateTime now = LocalDateTime.now();
        return new Stock(
                null,
                productId,
                productOptionId,
                initialQuantity,
                Quantity.zero(),
                memo,
                now,
                now);
    }

    /**
     * 기존 재고 복원 (DB에서 조회)
     */
    public static Stock restore(Long id, Long productId, Long productOptionId,
            Quantity availableQuantity, Quantity soldQuantity, String memo,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("재고 ID는 null일 수 없습니다.");
        }
        validateProductId(productId);
        validateQuantity(availableQuantity);
        validateQuantity(soldQuantity);

        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new Stock(id, productId, productOptionId, availableQuantity, soldQuantity, memo, createdAt, updatedAt);
    }

    /**
     * 재고 추가
     */
    public void addStock(Quantity quantity) {
        validateQuantity(quantity);
        if (quantity.isZero()) {
            throw new IllegalArgumentException("추가할 재고는 0보다 커야 합니다.");
        }

        this.availableQuantity = this.availableQuantity.add(quantity);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 차감 (주문 시)
     */
    public void reduceStock(Quantity quantity) {
        validateQuantity(quantity);
        if (quantity.isZero()) {
            throw new IllegalArgumentException("차감할 재고는 0보다 커야 합니다.");
        }
        if (!availableQuantity.isGreaterThanOrEqual(quantity)) {
            throw new IllegalArgumentException("재고가 부족합니다. 요청: " + quantity.getValue() +
                    ", 사용가능: " + availableQuantity.getValue());
        }

        this.availableQuantity = this.availableQuantity.subtract(quantity);
        this.soldQuantity = this.soldQuantity.add(quantity);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 복원 (주문 취소 시)
     */
    public void restoreStock(Quantity quantity) {
        validateQuantity(quantity);
        if (quantity.isZero()) {
            throw new IllegalArgumentException("복원할 재고는 0보다 커야 합니다.");
        }
        if (!soldQuantity.isGreaterThanOrEqual(quantity)) {
            throw new IllegalArgumentException("복원할 재고가 판매된 수량보다 클 수 없습니다. 요청: " + quantity.getValue() +
                    ", 판매됨: " + soldQuantity.getValue());
        }

        this.availableQuantity = this.availableQuantity.add(quantity);
        this.soldQuantity = this.soldQuantity.subtract(quantity);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 메모 수정
     */
    public void updateMemo(String memo) {
        this.memo = memo;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고가 충분한지 확인
     */
    public boolean hasEnoughStock(Quantity requestQuantity) {
        if (requestQuantity == null) {
            return false;
        }
        return availableQuantity.isGreaterThanOrEqual(requestQuantity);
    }

    /**
     * 재고가 없는지 확인
     */
    public boolean isEmpty() {
        return availableQuantity.isZero();
    }

    /**
     * 총 재고 계산
     */
    public Quantity getTotalQuantity() {
        return availableQuantity.add(soldQuantity);
    }

    /**
     * 상품 옵션 재고인지 확인
     */
    public boolean isProductOptionStock() {
        return productOptionId != null;
    }

    // 검증 메서드들
    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
    }

    private static void validateProductOptionId(Long productOptionId) {
        if (productOptionId == null) {
            throw new IllegalArgumentException("상품 옵션 ID는 필수입니다.");
        }
    }

    private static void validateQuantity(Quantity quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("수량은 필수입니다.");
        }
    }

    // Getter 메서드들
    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getProductOptionId() {
        return productOptionId;
    }

    public Quantity getAvailableQuantity() {
        return availableQuantity;
    }

    public Quantity getSoldQuantity() {
        return soldQuantity;
    }

    public String getMemo() {
        return memo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Stock stock = (Stock) o;
        return Objects.equals(id, stock.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Stock{" +
                "id=" + id +
                ", productId=" + productId +
                ", productOptionId=" + productOptionId +
                ", availableQuantity=" + availableQuantity +
                ", soldQuantity=" + soldQuantity +
                '}';
    }
}