package com.hanghae.ecommerce.domain.coupon;

import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CouponTest {

    @Test
    @DisplayName("쿠폰 생성 성공")
    void create_Success() {
        // given
        String name = "10% 할인 쿠폰";
        DiscountPolicy discountPolicy = DiscountPolicy.rate(10);
        Quantity totalQuantity = Quantity.of(100);
        LocalDateTime beginDate = LocalDateTime.now();
        LocalDateTime endDate = LocalDateTime.now().plusDays(7);

        // when
        Coupon coupon = Coupon.create(name, discountPolicy, totalQuantity, beginDate, endDate);

        // then
        assertThat(coupon.getName()).isEqualTo(name);
        assertThat(coupon.getState()).isEqualTo(CouponState.NORMAL);
        assertThat(coupon.getTotalQuantity()).isEqualTo(totalQuantity);
        assertThat(coupon.getIssuedQuantity().getValue()).isZero();
        assertThat(coupon.getRemainingQuantity().getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("빈 이름으로 쿠폰 생성 실패")
    void create_EmptyName() {
        // given
        String emptyName = "";
        DiscountPolicy discountPolicy = DiscountPolicy.rate(10);
        Quantity totalQuantity = Quantity.of(100);
        LocalDateTime beginDate = LocalDateTime.now();
        LocalDateTime endDate = LocalDateTime.now().plusDays(7);

        // when & then
        assertThatThrownBy(() -> Coupon.create(emptyName, discountPolicy, totalQuantity, beginDate, endDate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("쿠폰명은 필수입니다");
    }

    @Test
    @DisplayName("잘못된 날짜 범위로 쿠폰 생성 실패")
    void create_InvalidDateRange() {
        // given
        String name = "할인 쿠폰";
        DiscountPolicy discountPolicy = DiscountPolicy.rate(10);
        Quantity totalQuantity = Quantity.of(100);
        LocalDateTime beginDate = LocalDateTime.now();
        LocalDateTime endDate = beginDate.minusDays(1); // 시작일보다 이전 종료일

        // when & then
        assertThatThrownBy(() -> Coupon.create(name, discountPolicy, totalQuantity, beginDate, endDate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용 시작일시는 종료일시보다 이전이어야 합니다");
    }

    @Test
    @DisplayName("쿠폰 발급 성공")
    void issue_Success() {
        // given
        Coupon coupon = Coupon.create(
            "테스트 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(10),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );

        // when
        coupon.issue();

        // then
        assertThat(coupon.getIssuedQuantity().getValue()).isEqualTo(1);
        assertThat(coupon.getRemainingQuantity().getValue()).isEqualTo(9);
    }

    @Test
    @DisplayName("쿠폰 모두 소진 후 발급 실패")
    void issue_SoldOut() {
        // given
        Coupon coupon = Coupon.create(
            "소량 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(1),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        coupon.issue(); // 1개 발급으로 소진

        // when & then
        assertThatThrownBy(coupon::issue)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("쿠폰을 발급할 수 없습니다");
    }

    @Test
    @DisplayName("비활성 쿠폰 발급 실패")
    void issue_InactiveCoupon() {
        // given
        Coupon coupon = Coupon.create(
            "테스트 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(10),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        coupon.discontinue();

        // when & then
        assertThatThrownBy(coupon::issue)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("쿠폰을 발급할 수 없습니다");
    }

    @Test
    @DisplayName("만료된 쿠폰 발급 실패")
    void issue_ExpiredCoupon() {
        // given
        Coupon coupon = Coupon.create(
            "만료된 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(10),
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(1) // 어제 만료
        );

        // when & then
        assertThatThrownBy(coupon::issue)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("쿠폰을 발급할 수 없습니다");
    }

    @Test
    @DisplayName("쿠폰 만료 확인")
    void isWithinValidPeriod() {
        // given
        Coupon activeCoupon = Coupon.create(
            "활성 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(10),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );

        Coupon expiredCoupon = Coupon.create(
            "만료된 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(10),
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(1)
        );

        // when & then
        assertThat(activeCoupon.isWithinValidPeriod()).isTrue();
        assertThat(expiredCoupon.isWithinValidPeriod()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 상태 변경")
    void stateChange() {
        // given
        Coupon coupon = Coupon.create(
            "테스트 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(10),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );

        // when & then
        assertThat(coupon.getState()).isEqualTo(CouponState.NORMAL);

        coupon.discontinue();
        assertThat(coupon.getState()).isEqualTo(CouponState.DISCONTINUED);

        // 다시 생성하여 만료 처리 테스트
        Coupon newCoupon = Coupon.create(
            "테스트 쿠폰2",
            DiscountPolicy.rate(10),
            Quantity.of(10),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        newCoupon.expire();
        assertThat(newCoupon.getState()).isEqualTo(CouponState.EXPIRED);
    }

    @Test
    @DisplayName("쿠폰 발급 가능 여부 확인")
    void canIssue() {
        // given
        Coupon activeCoupon = Coupon.create(
            "활성 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(10),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );

        Coupon inactiveCoupon = Coupon.create(
            "비활성 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(10),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        inactiveCoupon.discontinue();

        Coupon soldOutCoupon = Coupon.create(
            "품절 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(1),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        soldOutCoupon.issue();

        // when & then
        assertThat(activeCoupon.canIssue()).isTrue();
        assertThat(inactiveCoupon.canIssue()).isFalse();
        assertThat(soldOutCoupon.canIssue()).isFalse();
    }
}