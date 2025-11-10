package com.hanghae.ecommerce.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 주문 생성 요청 DTO
 */
@Getter
@NoArgsConstructor
public class CreateOrderRequest {
    
    @NotEmpty(message = "주문할 장바구니 아이템을 선택해주세요")
    private List<Long> cartItemIds;
    
    @NotNull(message = "배송지 정보는 필수입니다")
    @Valid
    private ShippingAddress shippingAddress;

    public CreateOrderRequest(List<Long> cartItemIds, ShippingAddress shippingAddress) {
        this.cartItemIds = cartItemIds;
        this.shippingAddress = shippingAddress;
    }

    @Getter
    @NoArgsConstructor
    public static class ShippingAddress {
        
        @NotNull(message = "수령인 이름은 필수입니다")
        @Size(min = 1, max = 50, message = "수령인 이름은 1자 이상 50자 이하여야 합니다")
        private String recipientName;
        
        @NotNull(message = "연락처는 필수입니다")
        @Pattern(regexp = "^\\d{3}-\\d{4}-\\d{4}$", message = "연락처는 010-1234-5678 형식이어야 합니다")
        private String phone;
        
        @NotNull(message = "우편번호는 필수입니다")
        @Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자여야 합니다")
        private String zipCode;
        
        @NotNull(message = "주소는 필수입니다")
        @Size(min = 1, max = 200, message = "주소는 1자 이상 200자 이하여야 합니다")
        private String address;
        
        @Size(max = 100, message = "상세주소는 100자 이하여야 합니다")
        private String detailAddress;

        public ShippingAddress(String recipientName, String phone, String zipCode, 
                             String address, String detailAddress) {
            this.recipientName = recipientName;
            this.phone = phone;
            this.zipCode = zipCode;
            this.address = address;
            this.detailAddress = detailAddress;
        }
    }
}