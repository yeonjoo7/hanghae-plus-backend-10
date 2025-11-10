package com.hanghae.ecommerce.presentation.exception;

/**
 * 장바구니 아이템을 찾을 수 없을 때 발생하는 예외
 */
public class CartItemNotFoundException extends BusinessException {
    
    public CartItemNotFoundException(Long cartItemId) {
        super("CART_ITEM_NOT_FOUND", "장바구니 아이템을 찾을 수 없습니다", cartItemId);
    }
}