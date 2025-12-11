package com.hanghae.ecommerce.application.payment;

import com.hanghae.ecommerce.domain.payment.PaymentMethod;

import java.util.Map;

/**
 * 결제 완료 이벤트
 * 트랜잭션 완료 후 비동기로 처리할 부가 로직에 필요한 데이터를 담는다.
 */
public record PaymentCompletedEvent(
        String orderId,
        String userId,
        String orderNumber,
        long totalAmount,
        PaymentMethod paymentMethod,
        Map<Long, Integer> productOrderCounts
) {
}
