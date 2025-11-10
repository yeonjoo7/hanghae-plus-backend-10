package com.hanghae.ecommerce.presentation.exception;

import java.util.Map;

/**
 * 장바구니 제한 수량을 초과할 때 발생하는 예외
 */
public class ExceedMaxQuantityException extends BusinessException {
    
    public ExceedMaxQuantityException(int maxQuantityPerCart, int requestedQuantity) {
        super("EXCEED_MAX_QUANTITY", "장바구니 제한 수량을 초과했습니다", 
              Map.of("maxQuantityPerCart", maxQuantityPerCart, 
                     "requestedQuantity", requestedQuantity));
    }
}