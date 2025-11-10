package com.hanghae.ecommerce.presentation.exception;

/**
 * 이미 결제가 완료된 주문에 대해 다시 결제를 시도할 때 발생하는 예외
 */
public class PaymentAlreadyCompletedException extends BusinessException {
    
    public PaymentAlreadyCompletedException() {
        super("PAYMENT_ALREADY_COMPLETED", "이미 결제가 완료된 주문입니다");
    }
}