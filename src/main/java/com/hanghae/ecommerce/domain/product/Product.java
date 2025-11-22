package com.hanghae.ecommerce.domain.product;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 상품 도메인 엔티티
 */
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ProductState state;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    })
    private Money price;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "limited_quantity"))
    })
    private Quantity limitedQuantity; // 1인당 구매 제한 수량

    @Column(name = "created_at", nullable = false, updatable = false)
    private final LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Product(Long id, ProductState state, String name, String description,
            Money price, Quantity limitedQuantity, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.state = state;
        this.name = name;
        this.description = description;
        this.price = price;
        this.limitedQuantity = limitedQuantity;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 상품 생성
     */
    public static Product create(String name, String description, Money price, Quantity limitedQuantity) {
        validateName(name);
        validatePrice(price);

        LocalDateTime now = LocalDateTime.now();
        return new Product(
                null,
                ProductState.NORMAL,
                name,
                description,
                price,
                limitedQuantity,
                now,
                now);
    }

    /**
     * 기존 상품 복원 (DB에서 조회)
     */
    public static Product restore(Long id, ProductState state, String name, String description,
            Money price, Quantity limitedQuantity, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("상품 ID는 null일 수 없습니다.");
        }
        validateName(name);
        validatePrice(price);

        if (state == null) {
            throw new IllegalArgumentException("상품 상태는 null일 수 없습니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new Product(id, state, name, description, price, limitedQuantity, createdAt, updatedAt);
    }

    /**
     * 상품 정보 수정
     */
    public void updateInfo(String name, String description, Money price, Quantity limitedQuantity) {
        validateName(name);
        validatePrice(price);

        this.name = name;
        this.description = description;
        this.price = price;
        this.limitedQuantity = limitedQuantity;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 품절 처리
     */
    public void markOutOfStock() {
        if (state == ProductState.DELETED) {
            throw new IllegalStateException("삭제된 상품은 품절 처리할 수 없습니다.");
        }
        this.state = ProductState.OUT_OF_STOCK;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 재입고 처리
     */
    public void markInStock() {
        if (state == ProductState.DELETED) {
            throw new IllegalStateException("삭제된 상품은 재입고 처리할 수 없습니다.");
        }
        this.state = ProductState.NORMAL;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 단종 처리
     */
    public void discontinue() {
        if (state == ProductState.DELETED) {
            throw new IllegalStateException("삭제된 상품은 단종 처리할 수 없습니다.");
        }
        this.state = ProductState.DISCONTINUED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 삭제 (소프트 삭제)
     */
    public void delete() {
        this.state = ProductState.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 판매 가능한지 확인
     */
    public boolean isAvailable() {
        return state.isAvailable();
    }

    /**
     * 구매 제한 수량이 있는지 확인
     */
    public boolean hasLimitedQuantity() {
        return limitedQuantity != null && limitedQuantity.isPositive();
    }

    /**
     * 요청 수량이 구매 제한을 초과하는지 확인
     */
    public boolean exceedsLimitedQuantity(Quantity requestQuantity) {
        if (!hasLimitedQuantity()) {
            return false;
        }
        return requestQuantity.isGreaterThan(limitedQuantity);
    }

    // 검증 메서드들
    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("상품명은 200자를 초과할 수 없습니다.");
        }
    }

    private static void validatePrice(Money price) {
        if (price == null) {
            throw new IllegalArgumentException("상품 가격은 필수입니다.");
        }
        if (!price.isPositive()) {
            throw new IllegalArgumentException("상품 가격은 0보다 커야 합니다.");
        }
    }

    // Getter 메서드들
    public Long getId() {
        return id;
    }

    public ProductState getState() {
        return state;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public Quantity getLimitedQuantity() {
        return limitedQuantity;
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
        Product product = (Product) o;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", state=" + state +
                ", name='" + name + '\'' +
                ", price=" + price +
                '}';
    }
}