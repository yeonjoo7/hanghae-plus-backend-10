package com.hanghae.ecommerce.presentation.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private String paymentMethod;
    private List<Long> couponIds;
}
