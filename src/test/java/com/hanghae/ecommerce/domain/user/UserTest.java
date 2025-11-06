package com.hanghae.ecommerce.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    @DisplayName("사용자 생성 성공")
    void create_Success() {
        // given
        String email = "test@example.com";
        UserType userType = UserType.CUSTOMER;
        String name = "테스트 사용자";
        String phoneNumber = "010-1234-5678";

        // when
        User user = User.create(email, userType, name, phoneNumber);

        // then
        assertThat(user.getEmail().getValue()).isEqualTo(email);
        assertThat(user.getUserType()).isEqualTo(userType);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getPhoneNumber()).isEqualTo(phoneNumber);
        assertThat(user.getState()).isEqualTo(UserState.ACTIVE);
        assertThat(user.getBalance().getValue()).isZero();
    }

    @Test
    @DisplayName("잘못된 이메일로 사용자 생성 실패")
    void create_InvalidEmail() {
        // given
        String invalidEmail = "invalid-email";
        UserType userType = UserType.CUSTOMER;
        String name = "테스트 사용자";
        String phoneNumber = "010-1234-5678";

        // when & then
        assertThatThrownBy(() -> User.create(invalidEmail, userType, name, phoneNumber))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("올바른 이메일 형식이 아닙니다");
    }

    @Test
    @DisplayName("빈 이름으로 사용자 생성 실패")
    void create_EmptyName() {
        // given
        String email = "test@example.com";
        UserType userType = UserType.CUSTOMER;
        String emptyName = "";
        String phoneNumber = "010-1234-5678";

        // when & then
        assertThatThrownBy(() -> User.create(email, userType, emptyName, phoneNumber))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이름은 필수입니다");
    }

    @Test
    @DisplayName("잘못된 전화번호로 사용자 생성 실패")
    void create_InvalidPhoneNumber() {
        // given
        String email = "test@example.com";
        UserType userType = UserType.CUSTOMER;
        String name = "테스트 사용자";
        String invalidPhoneNumber = "123-456";

        // when & then
        assertThatThrownBy(() -> User.create(email, userType, name, invalidPhoneNumber))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("올바른 전화번호 형식이 아닙니다");
    }

    @Test
    @DisplayName("포인트 충전 성공")
    void chargePoint_Success() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        Point chargeAmount = Point.of(10000);

        // when
        user.chargePoint(chargeAmount);

        // then
        assertThat(user.getBalance().getValue()).isEqualTo(10000);
    }

    @Test
    @DisplayName("음수 포인트 충전 실패")
    void chargePoint_NegativeAmount() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        Point negativeAmount = Point.of(-1000);

        // when & then
        assertThatThrownBy(() -> user.chargePoint(negativeAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("충전 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("포인트 사용 성공")
    void usePoint_Success() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        user.chargePoint(Point.of(10000)); // 10,000 포인트 충전
        Point useAmount = Point.of(3000);

        // when
        user.usePoint(useAmount);

        // then
        assertThat(user.getBalance().getValue()).isEqualTo(7000);
    }

    @Test
    @DisplayName("잔액 부족으로 포인트 사용 실패")
    void usePoint_InsufficientBalance() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        user.chargePoint(Point.of(5000)); // 5,000 포인트만 충전
        Point useAmount = Point.of(10000); // 10,000 포인트 사용 시도

        // when & then
        assertThatThrownBy(() -> user.usePoint(useAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("잔액이 부족합니다");

        // 잔액은 변경되지 않아야 함
        assertThat(user.getBalance().getValue()).isEqualTo(5000);
    }

    @Test
    @DisplayName("음수 포인트 사용 실패")
    void usePoint_NegativeAmount() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        Point negativeAmount = Point.of(-1000);

        // when & then
        assertThatThrownBy(() -> user.usePoint(negativeAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("포인트 환불 성공")
    void refundPoint_Success() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        user.chargePoint(Point.of(10000));
        user.usePoint(Point.of(3000)); // 7,000 포인트 남음
        
        Point refundAmount = Point.of(2000);

        // when
        user.refundPoint(refundAmount);

        // then
        assertThat(user.getBalance().getValue()).isEqualTo(9000); // 7,000 + 2,000
    }

    @Test
    @DisplayName("음수 포인트 환불 실패")
    void refundPoint_NegativeAmount() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        Point negativeAmount = Point.of(-1000);

        // when & then
        assertThatThrownBy(() -> user.refundPoint(negativeAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("환불 금액은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("사용자 활성/비활성 상태 변경")
    void activateAndDeactivate() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");

        // when & then
        assertThat(user.getState()).isEqualTo(UserState.ACTIVE);
        assertThat(user.isActive()).isTrue();

        user.deactivate();
        assertThat(user.getState()).isEqualTo(UserState.INACTIVE);
        assertThat(user.isActive()).isFalse();

        user.activate();
        assertThat(user.getState()).isEqualTo(UserState.ACTIVE);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("사용자 정보 업데이트")
    void updateUserInfo() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "기존이름", "010-1111-1111");
        String newName = "새로운이름";
        String newPhoneNumber = "010-2222-2222";

        // when
        user.updateName(newName);
        user.updatePhoneNumber(newPhoneNumber);

        // then
        assertThat(user.getName()).isEqualTo(newName);
        assertThat(user.getPhoneNumber()).isEqualTo(newPhoneNumber);
    }

    @Test
    @DisplayName("빈 이름으로 업데이트 실패")
    void updateName_Empty() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "기존이름", "010-1111-1111");

        // when & then
        assertThatThrownBy(() -> user.updateName(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이름은 필수입니다");
    }

    @Test
    @DisplayName("잘못된 전화번호로 업데이트 실패")
    void updatePhoneNumber_Invalid() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1111-1111");

        // when & then
        assertThatThrownBy(() -> user.updatePhoneNumber("invalid-phone"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("올바른 전화번호 형식이 아닙니다");
    }

    @Test
    @DisplayName("충분한 잔액 확인")
    void hasSufficientBalance() {
        // given
        User user = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        user.chargePoint(Point.of(10000));

        // when & then
        assertThat(user.hasSufficientBalance(Point.of(5000))).isTrue();
        assertThat(user.hasSufficientBalance(Point.of(10000))).isTrue();
        assertThat(user.hasSufficientBalance(Point.of(15000))).isFalse();
    }
}