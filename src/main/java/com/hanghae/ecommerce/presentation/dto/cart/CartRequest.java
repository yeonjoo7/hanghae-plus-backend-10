package com.hanghae.ecommerce.presentation.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CartRequest {
    private Long productId;
    private Integer quantity;
}
