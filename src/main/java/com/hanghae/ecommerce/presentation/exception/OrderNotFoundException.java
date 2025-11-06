package com.hanghae.ecommerce.presentation.exception;

/**
 * 주문을 찾을 수 없을 때 발생하는 예외
 */
public class OrderNotFoundException extends BusinessException {
    
    public OrderNotFoundException(Long orderId) {
        super("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다", orderId);
    }
}