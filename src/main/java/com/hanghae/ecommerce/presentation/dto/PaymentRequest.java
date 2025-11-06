package com.hanghae.ecommerce.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 결제 요청 DTO
 */
@Getter
@NoArgsConstructor
public class PaymentRequest {
    
    @NotNull(message = "결제 수단은 필수입니다")
    @Pattern(regexp = "POINT", message = "지원하는 결제 수단은 POINT입니다")
    private String paymentMethod;
    
    private List<Long> couponIds;

    public PaymentRequest(String paymentMethod, List<Long> couponIds) {
        this.paymentMethod = paymentMethod;
        this.couponIds = couponIds;
    }
}