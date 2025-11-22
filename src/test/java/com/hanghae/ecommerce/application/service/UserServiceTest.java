package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.application.user.UserService;
import com.hanghae.ecommerce.domain.payment.BalanceTransaction;
import com.hanghae.ecommerce.domain.payment.TransactionType;
import com.hanghae.ecommerce.domain.payment.repository.BalanceTransactionRepository;
import com.hanghae.ecommerce.domain.user.Point;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.UserType;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BalanceTransactionRepository balanceTransactionRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.create(
                "test@example.com",
                "테스트 사용자",
                "010-1234-5678");
        testUser.chargePoint(Point.of(10000)); // 초기 10,000 포인트
    }

    @Test
    @DisplayName("사용자 조회 성공")
    void getUser_Success() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // when
        User result = userService.getUserById(userId);

        // then
        assertThat(result).isEqualTo(testUser);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 실패")
    void getUser_NotFound() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("사용자 잔액 조회 성공")
    void getUserBalance_Success() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // when
        Point balance = userService.getUserBalance(userId);

        // then
        assertThat(balance.getValue()).isEqualTo(10000);
    }

    @Test
    @DisplayName("포인트 충전 성공")
    void chargePoint_Success() {
        // given
        Long userId = 1L;
        Point chargeAmount = Point.of(5000);
        Point initialBalance = testUser.getAvailablePoint();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceTransactionRepository.save(any(BalanceTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        BalanceTransaction result = userService.chargePoint(userId, chargeAmount);

        // then
        assertThat(result.getType()).isEqualTo(TransactionType.CHARGE);
        assertThat(result.getAmount()).isEqualTo(chargeAmount);
        assertThat(result.getBalanceBefore()).isEqualTo(initialBalance);
        assertThat(result.getBalanceAfter().getValue()).isEqualTo(15000);
        assertThat(testUser.getAvailablePoint().getValue()).isEqualTo(15000);
    }

    @Test
    @DisplayName("잘못된 금액으로 포인트 충전 실패")
    void chargePoint_InvalidAmount() {
        // given
        Long userId = 1L;
        Point invalidAmount = Point.of(500); // 1000원 미만 금액

        // when & then
        assertThatThrownBy(() -> userService.chargePoint(userId, invalidAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("충전 금액은 1,000원 이상이어야 합니다");
    }

    @Test
    @DisplayName("포인트 사용 성공")
    void usePoint_Success() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        Point useAmount = Point.of(3000);
        Point initialBalance = testUser.getAvailablePoint();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceTransactionRepository.save(any(BalanceTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        BalanceTransaction result = userService.usePoint(userId, useAmount, orderId, "주문 결제");

        // then
        assertThat(result.getType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(result.getAmount()).isEqualTo(useAmount);
        assertThat(result.getBalanceBefore()).isEqualTo(initialBalance);
        assertThat(result.getBalanceAfter().getValue()).isEqualTo(7000);
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(testUser.getAvailablePoint().getValue()).isEqualTo(7000);
    }

    @Test
    @DisplayName("잔액 부족으로 포인트 사용 실패")
    void usePoint_InsufficientBalance() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        Point useAmount = Point.of(15000); // 잔액(10,000)보다 큰 금액

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // when & then
        assertThatThrownBy(() -> userService.usePoint(userId, useAmount, orderId, "주문 결제"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잔액이 부족합니다");
    }

    @Test
    @DisplayName("잘못된 금액으로 포인트 사용 실패")
    void usePoint_InvalidAmount() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        Point invalidAmount = Point.of(0); // 0 금액

        // when & then
        assertThatThrownBy(() -> userService.usePoint(userId, invalidAmount, orderId, "주문 결제"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("포인트 환불 성공")
    void refundPoint_Success() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        Point refundAmount = Point.of(3000);

        // 먼저 포인트를 사용한 상태로 만들기
        testUser.usePoint(Point.of(3000));
        Point balanceAfterUse = testUser.getAvailablePoint();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceTransactionRepository.save(any(BalanceTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        BalanceTransaction result = userService.refundPoint(userId, refundAmount, orderId);

        // then
        assertThat(result.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(result.getAmount()).isEqualTo(refundAmount);
        assertThat(result.getBalanceBefore()).isEqualTo(balanceAfterUse);
        assertThat(result.getBalanceAfter().getValue()).isEqualTo(10000); // 원래 잔액으로 복원
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(testUser.getAvailablePoint().getValue()).isEqualTo(10000);
    }

    @Test
    @DisplayName("잘못된 금액으로 포인트 환불 실패")
    void refundPoint_InvalidAmount() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        Point invalidAmount = Point.of(0); // 0 금액

        // when & then
        assertThatThrownBy(() -> userService.refundPoint(userId, invalidAmount, orderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("환불 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("거래 내역 조회 성공")
    void getTransactionHistory_Success() {
        // given
        Long userId = 1L;
        List<BalanceTransaction> transactions = new ArrayList<>();
        transactions.add(BalanceTransaction.createCharge(
                userId, Point.of(10000), Point.of(0), "초기 충전"));
        transactions.add(BalanceTransaction.createPayment(
                userId, 1L, Point.of(3000), Point.of(10000), "주문 결제"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(balanceTransactionRepository.findByUserId(userId))
                .thenReturn(transactions);

        // when
        List<BalanceTransaction> result = userService.getTransactionHistory(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getType()).isEqualTo(TransactionType.CHARGE);
        assertThat(result.get(1).getType()).isEqualTo(TransactionType.PAYMENT);
    }

    @Test
    @DisplayName("사용자 생성 성공")
    void createUser_Success() {
        // given
        String email = "new@example.com";
        String name = "신규 사용자";
        String phoneNumber = "010-9876-5432";

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        User result = userService.createUser(email, name, phoneNumber);

        // then
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getType()).isEqualTo(UserType.CUSTOMER);
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getPhone()).isEqualTo(phoneNumber);
        assertThat(result.getAvailablePoint().getValue()).isZero();
        verify(userRepository).save(any(User.class));
    }
}