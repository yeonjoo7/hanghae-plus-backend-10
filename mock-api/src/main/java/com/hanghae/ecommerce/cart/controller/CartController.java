package com.hanghae.ecommerce.cart.controller;

import com.hanghae.ecommerce.cart.dto.CartRequest;
import com.hanghae.ecommerce.cart.dto.CartResponse;
import com.hanghae.ecommerce.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/v1/carts")
public class CartController {

    private final Map<Long, CartResponse.CartItem> cartItems = new HashMap<>();
    private final AtomicLong itemIdGenerator = new AtomicLong(1);

    @GetMapping
    public ApiResponse<CartResponse> getCart() {
        List<CartResponse.CartItem> items = new ArrayList<>(cartItems.values());
        int totalAmount = items.stream()
                .mapToInt(CartResponse.CartItem::getSubtotal)
                .sum();

        CartResponse cart = CartResponse.builder()
                .cartId(1L)
                .userId(1L)
                .items(items)
                .totalAmount(totalAmount)
                .itemCount(items.size())
                .updatedAt(LocalDateTime.now())
                .build();

        return ApiResponse.success(cart);
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse.CartItem> addCartItem(@RequestBody CartRequest request) {
        // 재고 검증 (Mock)
        if (request.getQuantity() <= 0) {
            return ApiResponse.error("INVALID_INPUT", "수량은 1 이상이어야 합니다");
        }

        Long itemId = itemIdGenerator.getAndIncrement();
        CartResponse.CartItem item = CartResponse.CartItem.builder()
                .cartItemId(itemId)
                .productId(request.getProductId())
                .productName("상품 " + request.getProductId())
                .price(1500000)
                .quantity(request.getQuantity())
                .subtotal(1500000 * request.getQuantity())
                .stock(50)
                .maxQuantityPerCart(5)
                .build();

        cartItems.put(itemId, item);

        return ApiResponse.success(item, "장바구니에 상품이 추가되었습니다");
    }

    @PatchMapping("/items/{cartItemId}")
    public ApiResponse<Map<String, Object>> updateCartItem(
            @PathVariable Long cartItemId,
            @RequestBody CartRequest request) {

        CartResponse.CartItem item = cartItems.get(cartItemId);
        if (item == null) {
            return ApiResponse.error("CART_ITEM_NOT_FOUND", "장바구니 아이템을 찾을 수 없습니다");
        }

        int newSubtotal = item.getPrice() * request.getQuantity();
        CartResponse.CartItem updatedItem = CartResponse.CartItem.builder()
                .cartItemId(item.getCartItemId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .price(item.getPrice())
                .quantity(request.getQuantity())
                .subtotal(newSubtotal)
                .stock(item.getStock())
                .maxQuantityPerCart(item.getMaxQuantityPerCart())
                .build();

        cartItems.put(cartItemId, updatedItem);

        Map<String, Object> response = new HashMap<>();
        response.put("cartItemId", cartItemId);
        response.put("quantity", request.getQuantity());
        response.put("subtotal", newSubtotal);

        return ApiResponse.success(response);
    }

    @DeleteMapping("/items/{cartItemId}")
    public ApiResponse<Void> deleteCartItem(@PathVariable Long cartItemId) {
        if (!cartItems.containsKey(cartItemId)) {
            return ApiResponse.error("CART_ITEM_NOT_FOUND", "장바구니 아이템을 찾을 수 없습니다");
        }

        cartItems.remove(cartItemId);
        return ApiResponse.success(null);
    }
}
