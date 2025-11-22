package com.hanghae.ecommerce.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 수령인 정보를 나타내는 Value Object
 */
@Embeddable
public class Recipient {
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\d{2,3}-\\d{3,4}-\\d{4}$");

    @Column(name = "recipient_name", length = 100)
    private String name;

    @Column(name = "recipient_phone", length = 20)
    private String phone;

    // JPA를 위한 기본 생성자
    protected Recipient() {
    }

    private Recipient(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    /**
     * 수령인 정보 생성
     */
    public static Recipient of(String name, String phone) {
        validateName(name);
        validatePhone(phone);

        return new Recipient(name, phone);
    }

    // 검증 메서드들
    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("수령인 이름은 필수입니다.");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("수령인 이름은 100자를 초과할 수 없습니다.");
        }
    }

    private static void validatePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("수령인 연락처는 필수입니다.");
        }
        if (phone.length() > 20) {
            throw new IllegalArgumentException("수령인 연락처는 20자를 초과할 수 없습니다.");
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("유효하지 않은 연락처 형식입니다. (예: 010-1234-5678)");
        }
    }

    // Getter 메서드들
    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Recipient recipient = (Recipient) o;
        return Objects.equals(name, recipient.name) &&
                Objects.equals(phone, recipient.phone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, phone);
    }

    @Override
    public String toString() {
        return name + " (" + phone + ")";
    }
}