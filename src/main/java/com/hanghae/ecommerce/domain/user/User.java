package com.hanghae.ecommerce.domain.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 사용자 도메인 엔티티
 */
@Entity
@Table(name = "users")
public class User {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\d{2,3}-\\d{3,4}-\\d{4}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private final String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private UserState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private final UserType type;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "available_point", nullable = false))
    })
    private Point availablePoint;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "used_point", nullable = false))
    })
    private Point usedPoint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private final LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private User(Long id, String email, UserState state, UserType type, String name,
            String phone, Point availablePoint, Point usedPoint,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.state = state;
        this.type = type;
        this.name = name;
        this.phone = phone;
        this.availablePoint = availablePoint;
        this.usedPoint = usedPoint;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 사용자 생성 (회원가입)
     */
    public static User create(String email, String name, String phone) {
        validateEmail(email);
        validateName(name);
        validatePhone(phone);

        LocalDateTime now = LocalDateTime.now();
        return new User(
                null,
                email,
                UserState.NORMAL,
                UserType.CUSTOMER,
                name,
                phone,
                Point.zero(),
                Point.zero(),
                now,
                now);
    }

    /**
     * 기존 사용자 복원 (DB에서 조회)
     */
    public static User restore(Long id, String email, UserState state, UserType type,
            String name, String phone, Point availablePoint, Point usedPoint,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("사용자 ID는 null일 수 없습니다.");
        }
        validateEmail(email);
        validateName(name);
        validatePhone(phone);

        if (state == null) {
            throw new IllegalArgumentException("사용자 상태는 null일 수 없습니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("사용자 타입은 null일 수 없습니다.");
        }
        if (availablePoint == null) {
            throw new IllegalArgumentException("사용 가능 포인트는 null일 수 없습니다.");
        }
        if (usedPoint == null) {
            throw new IllegalArgumentException("사용된 포인트는 null일 수 없습니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("생성일시는 null일 수 없습니다.");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("수정일시는 null일 수 없습니다.");
        }

        return new User(id, email, state, type, name, phone, availablePoint, usedPoint, createdAt, updatedAt);
    }

    /**
     * 포인트 충전
     */
    public void chargePoint(Point amount) {
        if (amount == null) {
            throw new IllegalArgumentException("충전할 포인트는 null일 수 없습니다.");
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("충전할 포인트는 0보다 커야 합니다.");
        }
        if (!isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 사용자는 포인트를 충전할 수 없습니다.");
        }

        this.availablePoint = this.availablePoint.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 포인트 사용
     */
    public void usePoint(Point amount) {
        if (amount == null) {
            throw new IllegalArgumentException("사용할 포인트는 null일 수 없습니다.");
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("사용할 포인트는 0보다 커야 합니다.");
        }
        if (!isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 사용자는 포인트를 사용할 수 없습니다.");
        }
        if (!availablePoint.isGreaterThanOrEqual(amount)) {
            throw new IllegalArgumentException("사용 가능한 포인트가 부족합니다.");
        }

        this.availablePoint = this.availablePoint.subtract(amount);
        this.usedPoint = this.usedPoint.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 포인트 환불
     */
    public void refundPoint(Point amount) {
        if (amount == null) {
            throw new IllegalArgumentException("환불할 포인트는 null일 수 없습니다.");
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("환불할 포인트는 0보다 커야 합니다.");
        }
        if (!isActive()) {
            throw new IllegalStateException("활성 상태가 아닌 사용자는 포인트를 환불받을 수 없습니다.");
        }
        if (!usedPoint.isGreaterThanOrEqual(amount)) {
            throw new IllegalArgumentException("환불할 포인트가 사용된 포인트보다 클 수 없습니다.");
        }

        this.availablePoint = this.availablePoint.add(amount);
        this.usedPoint = this.usedPoint.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 사용자 정보 수정
     */
    public void updateProfile(String name, String phone) {
        validateName(name);
        validatePhone(phone);

        this.name = name;
        this.phone = phone;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 사용자 비활성화
     */
    public void deactivate() {
        if (state == UserState.DELETED) {
            throw new IllegalStateException("삭제된 사용자는 비활성화할 수 없습니다.");
        }
        this.state = UserState.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 사용자 활성화
     */
    public void activate() {
        if (state == UserState.DELETED) {
            throw new IllegalStateException("삭제된 사용자는 활성화할 수 없습니다.");
        }
        this.state = UserState.NORMAL;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 사용자 삭제 (소프트 삭제)
     */
    public void delete() {
        this.state = UserState.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 활성 상태인지 확인
     */
    public boolean isActive() {
        return state.isActive();
    }

    /**
     * 관리자인지 확인
     */
    public boolean isAdmin() {
        return type.isAdmin();
    }

    /**
     * 총 포인트 계산
     */
    public Point getTotalPoint() {
        return availablePoint.add(usedPoint);
    }

    // 검증 메서드들
    private static void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (email.length() > 255) {
            throw new IllegalArgumentException("이메일은 255자를 초과할 수 없습니다.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("유효하지 않은 이메일 형식입니다.");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("이름은 필수입니다.");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("이름은 100자를 초과할 수 없습니다.");
        }
    }

    private static void validatePhone(String phone) {
        if (phone != null && !phone.trim().isEmpty()) {
            if (phone.length() > 20) {
                throw new IllegalArgumentException("연락처는 20자를 초과할 수 없습니다.");
            }
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                throw new IllegalArgumentException("유효하지 않은 연락처 형식입니다. (예: 010-1234-5678)");
            }
        }
    }

    // Getter 메서드들
    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public UserState getState() {
        return state;
    }

    public UserType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public Point getAvailablePoint() {
        return availablePoint;
    }

    public Point getUsedPoint() {
        return usedPoint;
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
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", state=" + state +
                ", type=" + type +
                ", name='" + name + '\'' +
                '}';
    }
}