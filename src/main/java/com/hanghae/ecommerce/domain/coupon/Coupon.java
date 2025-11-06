package com.hanghae.ecommerce.domain.coupon;

import com.hanghae.ecommerce.domain.product.Quantity;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 쿠폰 도메인 엔티티
 */
public class Coupon {
    private final Long id;
    private String name;
    private CouponState state;
    private DiscountPolicy discountPolicy;
    private Quantity totalQuantity;
    private Quantity issuedQuantity;
    private LocalDateTime beginDate;
    private LocalDateTime endDate;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Coupon(Long id, String name, CouponState state, DiscountPolicy discountPolicy,
                  Quantity totalQuantity, Quantity issuedQuantity, LocalDateTime beginDate,
                  LocalDateTime endDate, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.state = state;
        this.discountPolicy = discountPolicy;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = issuedQuantity;
        this.beginDate = beginDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 쿠폰 생성
     */
    public static Coupon create(String name, DiscountPolicy discountPolicy, Quantity totalQuantity,
                               LocalDateTime beginDate, LocalDateTime endDate) {
        validateName(name);
        validateDiscountPolicy(discountPolicy);
        validateTotalQuantity(totalQuantity);
        validateDateRange(beginDate, endDate);

        LocalDateTime now = LocalDateTime.now();
        return new Coupon(
            null,
            name,
            CouponState.NORMAL,
            discountPolicy,
            totalQuantity,
            Quantity.zero(),
            beginDate,
            endDate,
            now,
            now
        );
    }

    /**
     * 기존 쿠폰 복원 (DB에서 조회)
     */
    public static Coupon restore(Long id, String name, CouponState state, DiscountPolicy discountPolicy,
                                Quantity totalQuantity, Quantity issuedQuantity, LocalDateTime beginDate,
                                LocalDateTime endDate, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("쿠폰 ID는 null일 수 없습니다.");
        }
        validateName(name);
        validateDiscountPolicy(discountPolicy);
        validateTotalQuantity(totalQuantity);
        validateIssuedQuantity(issuedQuantity);
        validateDateRange(beginDate, endDate);
        
        if (state == null) {
            throw new IllegalArgumentException("쿠폰 상태는 null일 수 없습니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new Coupon(id, name, state, discountPolicy, totalQuantity, issuedQuantity,
                         beginDate, endDate, createdAt, updatedAt);
    }

    /**
     * 쿠폰 정보 수정
     */
    public void updateInfo(String name, DiscountPolicy discountPolicy, Quantity totalQuantity,
                          LocalDateTime beginDate, LocalDateTime endDate) {
        if (state == CouponState.DELETED) {
            throw new IllegalStateException("삭제된 쿠폰은 수정할 수 없습니다.");
        }
        if (issuedQuantity.isGreaterThan(totalQuantity)) {
            throw new IllegalArgumentException("총 수량은 이미 발급된 수량보다 작을 수 없습니다.");
        }

        validateName(name);
        validateDiscountPolicy(discountPolicy);
        validateTotalQuantity(totalQuantity);
        validateDateRange(beginDate, endDate);

        this.name = name;
        this.discountPolicy = discountPolicy;
        this.totalQuantity = totalQuantity;
        this.beginDate = beginDate;
        this.endDate = endDate;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 발급
     */
    public void issue() {
        if (!canIssue()) {
            throw new IllegalStateException("쿠폰을 발급할 수 없습니다. 상태: " + state);
        }
        if (!hasRemainingQuantity()) {
            throw new IllegalStateException("발급 가능한 쿠폰이 없습니다.");
        }

        this.issuedQuantity = this.issuedQuantity.add(Quantity.of(1));
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 만료 처리
     */
    public void expire() {
        if (state == CouponState.DELETED) {
            throw new IllegalStateException("삭제된 쿠폰은 만료 처리할 수 없습니다.");
        }
        this.state = CouponState.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 중단 처리
     */
    public void discontinue() {
        if (state == CouponState.DELETED) {
            throw new IllegalStateException("삭제된 쿠폰은 중단 처리할 수 없습니다.");
        }
        this.state = CouponState.DISCONTINUED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 삭제 (소프트 삭제)
     */
    public void delete() {
        this.state = CouponState.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 발급 가능 여부 확인
     */
    public boolean canIssue() {
        return state.isIssuable() && isWithinValidPeriod() && hasRemainingQuantity();
    }

    /**
     * 유효 기간 내인지 확인
     */
    public boolean isWithinValidPeriod() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(beginDate) && !now.isAfter(endDate);
    }

    /**
     * 남은 수량이 있는지 확인
     */
    public boolean hasRemainingQuantity() {
        return totalQuantity.isGreaterThan(issuedQuantity);
    }

    /**
     * 남은 수량 계산
     */
    public Quantity getRemainingQuantity() {
        return totalQuantity.subtract(issuedQuantity);
    }

    // 검증 메서드들
    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("쿠폰명은 필수입니다.");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("쿠폰명은 200자를 초과할 수 없습니다.");
        }
    }

    private static void validateDiscountPolicy(DiscountPolicy discountPolicy) {
        if (discountPolicy == null) {
            throw new IllegalArgumentException("할인 정책은 필수입니다.");
        }
    }

    private static void validateTotalQuantity(Quantity totalQuantity) {
        if (totalQuantity == null) {
            throw new IllegalArgumentException("총 발급 수량은 필수입니다.");
        }
        if (!totalQuantity.isPositive()) {
            throw new IllegalArgumentException("총 발급 수량은 0보다 커야 합니다.");
        }
    }

    private static void validateIssuedQuantity(Quantity issuedQuantity) {
        if (issuedQuantity == null) {
            throw new IllegalArgumentException("발급된 수량은 필수입니다.");
        }
    }

    private static void validateDateRange(LocalDateTime beginDate, LocalDateTime endDate) {
        if (beginDate == null) {
            throw new IllegalArgumentException("사용 시작일시는 필수입니다.");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("사용 종료일시는 필수입니다.");
        }
        if (!beginDate.isBefore(endDate)) {
            throw new IllegalArgumentException("사용 시작일시는 종료일시보다 이전이어야 합니다.");
        }
    }

    // Getter 메서드들
    public Long getId() { return id; }
    public String getName() { return name; }
    public CouponState getState() { return state; }
    public DiscountPolicy getDiscountPolicy() { return discountPolicy; }
    public Quantity getTotalQuantity() { return totalQuantity; }
    public Quantity getIssuedQuantity() { return issuedQuantity; }
    public LocalDateTime getBeginDate() { return beginDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coupon coupon = (Coupon) o;
        return Objects.equals(id, coupon.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Coupon{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", state=" + state +
                ", discountPolicy=" + discountPolicy +
                '}';
    }
}