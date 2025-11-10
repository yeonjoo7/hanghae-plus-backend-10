package com.hanghae.ecommerce.presentation.exception;

/**
 * 상품을 찾을 수 없을 때 발생하는 예외
 */
public class ProductNotFoundException extends BusinessException {
    
    public ProductNotFoundException(Long productId) {
        super("PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다", productId);
    }
}