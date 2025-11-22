package com.hanghae.ecommerce.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * 주소를 나타내는 Value Object
 */
@Embeddable
public class Address {
    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "detail_address", length = 500)
    private String detailAddress;

    // JPA를 위한 기본 생성자
    protected Address() {
    }

    private Address(String zipCode, String address, String detailAddress) {
        this.zipCode = zipCode;
        this.address = address;
        this.detailAddress = detailAddress;
    }

    /**
     * 주소 생성
     */
    public static Address of(String zipCode, String address, String detailAddress) {
        validateZipCode(zipCode);
        validateAddress(address);
        validateDetailAddress(detailAddress);

        return new Address(zipCode, address, detailAddress);
    }

    /**
     * 전체 주소 문자열 반환
     */
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(zipCode).append("] ");
        sb.append(address);
        if (detailAddress != null && !detailAddress.trim().isEmpty()) {
            sb.append(" ").append(detailAddress);
        }
        return sb.toString();
    }

    // 검증 메서드들
    private static void validateZipCode(String zipCode) {
        if (zipCode == null || zipCode.trim().isEmpty()) {
            throw new IllegalArgumentException("우편번호는 필수입니다.");
        }
        if (zipCode.length() > 10) {
            throw new IllegalArgumentException("우편번호는 10자를 초과할 수 없습니다.");
        }
    }

    private static void validateAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }
        if (address.length() > 500) {
            throw new IllegalArgumentException("주소는 500자를 초과할 수 없습니다.");
        }
    }

    private static void validateDetailAddress(String detailAddress) {
        if (detailAddress != null && detailAddress.length() > 500) {
            throw new IllegalArgumentException("상세 주소는 500자를 초과할 수 없습니다.");
        }
    }

    // Getter 메서드들
    public String getZipCode() {
        return zipCode;
    }

    public String getAddress() {
        return address;
    }

    public String getDetailAddress() {
        return detailAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Address address1 = (Address) o;
        return Objects.equals(zipCode, address1.zipCode) &&
                Objects.equals(address, address1.address) &&
                Objects.equals(detailAddress, address1.detailAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zipCode, address, detailAddress);
    }

    @Override
    public String toString() {
        return getFullAddress();
    }
}