package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.CouponState;
import com.hanghae.ecommerce.domain.coupon.DiscountPolicy;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.UserType;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LockManager lockManager;

    @InjectMocks
    private CouponService couponService;

    private User testUser;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        testCoupon = Coupon.create(
            "테스트 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(100),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
    }

    @Test
    @DisplayName("쿠폰 발급 성공")
    void issueCoupon_Success() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(testCoupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserCoupon result = couponService.issueCoupon(userId, couponId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getCouponId()).isEqualTo(couponId);
        verify(userCouponRepository).save(any(UserCoupon.class));
        verify(couponRepository).save(any(Coupon.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 쿠폰 발급 실패")
    void issueCoupon_UserNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 발급 실패")
    void issueCoupon_CouponNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(couponRepository.findById(couponId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰 중복 발급 실패")
    void issueCoupon_AlreadyIssued() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(testCoupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 발급받은 쿠폰입니다");
    }

    @Test
    @DisplayName("쿠폰 소진 시 발급 실패")
    void issueCoupon_SoldOut() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        // 쿠폰을 모두 발급 완료 상태로 만들기
        Coupon soldOutCoupon = Coupon.create(
            "품절 쿠폰",
            DiscountPolicy.rate(10),
            Quantity.of(1),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7)
        );
        soldOutCoupon.issue(); // 1개 발급으로 소진

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(soldOutCoupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("쿠폰이 모두 소진되었습니다");
    }

    @Test
    @DisplayName("사용자 쿠폰 목록 조회")
    void getUserCoupons_Success() {
        // given
        Long userId = 1L;
        List<UserCoupon> userCoupons = List.of(
            UserCoupon.create(userId, 1L),
            UserCoupon.create(userId, 2L)
        );

        when(userCouponRepository.findByUserId(userId)).thenReturn(userCoupons);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(testCoupon));
        when(couponRepository.findById(2L)).thenReturn(Optional.of(testCoupon));

        // when
        List<CouponService.UserCouponInfo> result = couponService.getUserCoupons(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(info -> info.getUserCoupon().getUserId().equals(userId));
    }

    @Test
    @DisplayName("쿠폰 사용 성공")
    void useCoupon_Success() {
        // given
        Long userId = 1L;
        Long userCouponId = 1L;

        UserCoupon userCoupon = UserCoupon.create(userId, 1L);
        when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        couponService.useCoupon(userId, userCouponId);

        // then
        assertThat(userCoupon.getState()).isEqualTo(UserCouponState.USED);
        verify(userCouponRepository).save(userCoupon);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 쿠폰 사용 실패")
    void useCoupon_UserCouponNotFound() {
        // given
        Long userId = 1L;
        Long userCouponId = 1L;

        when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponService.useCoupon(userId, userCouponId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용자 쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("본인 쿠폰이 아닌 경우 사용 실패")
    void useCoupon_NotOwner() {
        // given
        Long userId = 1L;
        Long userCouponId = 1L;
        Long otherUserId = 2L;

        UserCoupon userCoupon = UserCoupon.create(otherUserId, 1L);
        when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));

        // when & then
        assertThatThrownBy(() -> couponService.useCoupon(userId, userCouponId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("본인의 쿠폰이 아닙니다");
    }
}