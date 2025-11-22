package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.DiscountPolicy;
import com.hanghae.ecommerce.domain.coupon.UserCouponInfo;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.application.coupon.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CouponServiceImpl couponService;

    private User testUser;
    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", "테스트", "010-1234-5678");
        testCoupon = Coupon.create(
                "테스트 쿠폰",
                DiscountPolicy.rate(10),
                Quantity.of(100),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7));
    }

    @Test
    @DisplayName("쿠폰 발급 성공")
    void issueCoupon_Success() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Mock JdbcTemplate for coupon query
        Map<String, Object> couponMap = java.util.Map.of(
                "total_quantity", 100,
                "issued_quantity", 0);
        // Use any() for the varargs
        when(jdbcTemplate.queryForList(anyString(), any(Object.class))).thenReturn(List.of(couponMap));

        when(userCouponRepository.findByUserIdAndCouponId(userId, couponId)).thenReturn(List.of());
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // couponRepository.save is not called in implementation anymore, it uses
        // jdbcTemplate.update

        // when
        UserCoupon result = couponService.issueCoupon(couponId, userId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getCouponId()).isEqualTo(couponId);
        verify(userCouponRepository).save(any(UserCoupon.class));
        verify(jdbcTemplate).update(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 쿠폰 발급 실패")
    void issueCoupon_UserNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다. ID: " + userId);
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 발급 실패")
    void issueCoupon_CouponNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(jdbcTemplate.queryForList(anyString(), eq(couponId))).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(com.hanghae.ecommerce.presentation.exception.CouponNotFoundException.class);
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰 중복 발급 실패")
    void issueCoupon_AlreadyIssued() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        Map<String, Object> couponMap = java.util.Map.of(
                "total_quantity", 100,
                "issued_quantity", 0);
        when(jdbcTemplate.queryForList(anyString(), any(Object.class))).thenReturn(List.of(couponMap));

        // Mock existing coupon
        UserCoupon existingCoupon = UserCoupon.issue(userId, couponId, LocalDateTime.now().plusDays(7));
        when(userCouponRepository.findByUserIdAndCouponId(userId, couponId)).thenReturn(List.of(existingCoupon));

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(com.hanghae.ecommerce.presentation.exception.CouponAlreadyIssuedException.class);
    }

    @Test
    @DisplayName("쿠폰 소진 시 발급 실패")
    void issueCoupon_SoldOut() {
        // given
        Long userId = 1L;
        Long couponId = 1L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        // Mock JdbcTemplate to return empty list (query has condition issued < total)
        when(jdbcTemplate.queryForList(anyString(), eq(couponId))).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(com.hanghae.ecommerce.presentation.exception.CouponNotFoundException.class);
        // Note: The implementation throws CouponNotFoundException if query returns
        // empty,
        // which happens if issued >= total due to the WHERE clause.
    }

    @Test
    @DisplayName("사용자 쿠폰 목록 조회")
    void getUserCoupons_Success() {
        // given
        Long userId = 1L;
        List<UserCoupon> userCoupons = List.of(
                UserCoupon.issue(userId, 1L, LocalDateTime.now().plusDays(7)),
                UserCoupon.issue(userId, 2L, LocalDateTime.now().plusDays(7)));

        // Removed unnecessary userRepository stub
        when(userCouponRepository.findByUserId(userId)).thenReturn(userCoupons);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(testCoupon));
        when(couponRepository.findById(2L)).thenReturn(Optional.of(testCoupon));

        // when
        List<UserCouponInfo> result = couponService.getUserCoupons(userId);

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

        UserCoupon userCoupon = UserCoupon.issue(userId, 1L, LocalDateTime.now().plusDays(7));
        when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        couponService.useCoupon(userCouponId, userId);

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
        assertThatThrownBy(() -> couponService.useCoupon(userCouponId, userId))
                .isInstanceOf(com.hanghae.ecommerce.presentation.exception.CouponNotFoundException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("본인 쿠폰이 아닌 경우 사용 실패")
    void useCoupon_NotOwner() {
        // given
        Long userId = 1L;
        Long userCouponId = 1L;
        Long otherUserId = 2L;

        UserCoupon userCoupon = UserCoupon.issue(otherUserId, 1L, LocalDateTime.now().plusDays(7));
        when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));

        // when & then
        assertThatThrownBy(() -> couponService.useCoupon(userCouponId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 쿠폰의 소유자가 아닙니다");
    }
}