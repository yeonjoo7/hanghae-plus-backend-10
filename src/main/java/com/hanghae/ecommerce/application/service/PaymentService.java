package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.presentation.dto.PaymentResultDto;

import java.util.List;

public interface PaymentService {
    
    PaymentResultDto processPayment(Long userId, Long orderId, PaymentMethod paymentMethod, List<Long> couponIds);
    
    Payment processPayment(String orderId, String userId, PaymentMethod paymentMethod);
    
    Payment getPayment(String paymentId);
    
    List<Payment> getPaymentsByOrderId(String orderId);
}