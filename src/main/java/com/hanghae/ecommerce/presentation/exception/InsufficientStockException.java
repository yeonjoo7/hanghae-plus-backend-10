package com.hanghae.ecommerce.presentation.exception;

import java.util.Map;

/**
 * 재고가 부족할 때 발생하는 예외
 */
public class InsufficientStockException extends BusinessException {
    
    public InsufficientStockException(int requestedQuantity, int availableStock) {
        super("INSUFFICIENT_STOCK", "재고가 부족합니다", 
              Map.of("requestedQuantity", requestedQuantity, 
                     "availableStock", availableStock));
    }
}