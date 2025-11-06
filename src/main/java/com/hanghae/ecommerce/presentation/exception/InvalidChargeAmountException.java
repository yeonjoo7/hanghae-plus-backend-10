package com.hanghae.ecommerce.presentation.exception;

import java.util.Map;

/**
 * 유효하지 않은 충전 금액일 때 발생하는 예외
 */
public class InvalidChargeAmountException extends BusinessException {
    
    public InvalidChargeAmountException(int minAmount, int requestedAmount) {
        super("INVALID_CHARGE_AMOUNT", "충전 금액은 1,000원 이상이어야 합니다", 
              Map.of("minAmount", minAmount, 
                     "requestedAmount", requestedAmount));
    }
}