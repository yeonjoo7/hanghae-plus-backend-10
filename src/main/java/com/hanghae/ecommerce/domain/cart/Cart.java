package com.hanghae.ecommerce.domain.cart;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 장바구니 도메인 엔티티
 */
public class Cart {
    private final Long id;
    private final Long userId;
    private Long userCouponId; // 적용된 사용자 쿠폰 ID
    private CartState state;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Cart(Long id, Long userId, Long userCouponId, CartState state,
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.userCouponId = userCouponId;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 장바구니 생성
     */
    public static Cart create(Long userId) {
        validateUserId(userId);

        LocalDateTime now = LocalDateTime.now();
        return new Cart(
            null,
            userId,
            null,
            CartState.NORMAL,
            now,
            now
        );
    }

    /**
     * 기존 장바구니 복원 (DB에서 조회)
     */
    public static Cart restore(Long id, Long userId, Long userCouponId, CartState state,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("장바구니 ID는 null일 수 없습니다.");
        }
        validateUserId(userId);
        
        if (state == null) {
            throw new IllegalArgumentException("장바구니 상태는 null일 수 없습니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new Cart(id, userId, userCouponId, state, createdAt, updatedAt);
    }

    /**
     * 쿠폰 적용
     */
    public void applyCoupon(Long userCouponId) {
        if (!isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 장바구니에는 쿠폰을 적용할 수 없습니다.");
        }
        if (userCouponId == null) {
            throw new IllegalArgumentException("사용자 쿠폰 ID는 null일 수 없습니다.");
        }

        this.userCouponId = userCouponId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 제거
     */
    public void removeCoupon() {
        if (!isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 장바구니의 쿠폰은 제거할 수 없습니다.");
        }

        this.userCouponId = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 완료 처리
     */
    public void markAsOrdered() {
        if (!isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 장바구니는 주문 완료 처리할 수 없습니다.");
        }

        this.state = CartState.ORDERED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 장바구니 삭제 (소프트 삭제)
     */
    public void delete() {
        this.state = CartState.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 활성 상태인지 확인
     */
    public boolean isActive() {
        return state.isActive();
    }

    /**
     * 쿠폰이 적용되어 있는지 확인
     */
    public boolean hasCouponApplied() {
        return userCouponId != null;
    }

    /**
     * 장바구니가 비어있는지 확인 (비즈니스 로직)
     * 실제로는 CartItem 조회가 필요하지만, 엔티티 자체에서는 상태만 확인
     */
    public boolean canBeOrdered() {
        return isActive();
    }

    // 검증 메서드들
    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
    }

    // Getter 메서드들
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getUserCouponId() { return userCouponId; }
    public CartState getState() { return state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cart cart = (Cart) o;
        return Objects.equals(id, cart.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Cart{" +
                "id=" + id +
                ", userId=" + userId +
                ", userCouponId=" + userCouponId +
                ", state=" + state +
                '}';
    }
}