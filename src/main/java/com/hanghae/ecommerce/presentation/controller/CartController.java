package com.hanghae.ecommerce.presentation.controller;

import com.hanghae.ecommerce.application.cart.CartService;
import com.hanghae.ecommerce.application.cart.CartService.CartInfo;
import com.hanghae.ecommerce.application.cart.CartService.CartItemInfo;
import com.hanghae.ecommerce.application.product.StockService;
import com.hanghae.ecommerce.common.ApiResponse;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.presentation.dto.*;
import com.hanghae.ecommerce.presentation.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 장바구니 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final StockService stockService;

    // TODO: 현재는 임시로 userId를 1L로 고정. 실제로는 인증된 사용자 정보에서 가져와야 함
    private static final Long CURRENT_USER_ID = 1L;

    /**
     * 장바구니 조회
     * GET /carts
     */
    @GetMapping
    public ApiResponse<CartResponse> getCart() {
        CartInfo cartInfo = cartService.getCartInfo(CURRENT_USER_ID);
        
        List<CartResponse.CartItemResponse> items = cartInfo.getItems().stream()
                .map(this::toCartItemResponse)
                .collect(Collectors.toList());
        
        CartResponse response = new CartResponse(
            cartInfo.getCart().getId(),
            cartInfo.getCart().getUserId(),
            items,
            cartInfo.getTotalAmount().getValue(),
            cartInfo.getItemCount(),
            cartInfo.getCart().getUpdatedAt()
        );
        
        return ApiResponse.success(response);
    }

    /**
     * 장바구니에 상품 추가
     * POST /carts/items
     */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddCartItemResponse> addCartItem(@Valid @RequestBody AddCartItemRequest request) {
        try {
            // 재고 확인
            Stock stock = stockService.getStock(request.getProductId());
            if (!stock.hasEnoughStock(com.hanghae.ecommerce.domain.product.Quantity.of(request.getQuantity()))) {
                throw new InsufficientStockException(
                    request.getQuantity(),
                    stock.getAvailableQuantity().getValue()
                );
            }
            
            CartItemInfo cartItemInfo = cartService.addItemToCart(
                CURRENT_USER_ID,
                request.getProductId(),
                request.getQuantity()
            );
            
            AddCartItemResponse response = new AddCartItemResponse(
                cartItemInfo.getCartItemId(),
                cartItemInfo.getProductId(),
                cartItemInfo.getProductName(),
                cartItemInfo.getQuantity(),
                cartItemInfo.getPrice().getValue(),
                cartItemInfo.getSubtotal().getValue()
            );
            
            return ApiResponse.success(response, "장바구니에 상품이 추가되었습니다");
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("상품을 찾을 수 없습니다")) {
                throw new ProductNotFoundException(request.getProductId());
            } else if (e.getMessage().contains("제한 수량")) {
                // 메시지에서 제한 수량과 요청 수량 추출 시도 (간단한 구현)
                throw new ExceedMaxQuantityException(5, request.getQuantity()); // 기본값 사용
            }
            throw e;
        }
    }

    /**
     * 장바구니 상품 수량 변경
     * PATCH /carts/items/{cartItemId}
     */
    @PatchMapping("/items/{cartItemId}")
    public ApiResponse<UpdateCartItemResponse> updateCartItem(
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        try {
            CartItemInfo cartItemInfo = cartService.updateCartItemQuantity(
                CURRENT_USER_ID,
                cartItemId,
                request.getQuantity()
            );
            
            // 재고 확인
            Stock stock = stockService.getStock(cartItemInfo.getProductId());
            if (!stock.hasEnoughStock(com.hanghae.ecommerce.domain.product.Quantity.of(request.getQuantity()))) {
                throw new InsufficientStockException(
                    request.getQuantity(),
                    stock.getAvailableQuantity().getValue()
                );
            }
            
            UpdateCartItemResponse response = new UpdateCartItemResponse(
                cartItemInfo.getCartItemId(),
                cartItemInfo.getQuantity(),
                cartItemInfo.getSubtotal().getValue()
            );
            
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("장바구니 아이템을 찾을 수 없습니다")) {
                throw new CartItemNotFoundException(cartItemId);
            } else if (e.getMessage().contains("제한 수량")) {
                throw new ExceedMaxQuantityException(5, request.getQuantity()); // 기본값 사용
            }
            throw e;
        }
    }

    /**
     * 장바구니 상품 삭제
     * DELETE /carts/items/{cartItemId}
     */
    @DeleteMapping("/items/{cartItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCartItem(@PathVariable Long cartItemId) {
        try {
            cartService.removeCartItem(CURRENT_USER_ID, cartItemId);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("장바구니 아이템을 찾을 수 없습니다")) {
                throw new CartItemNotFoundException(cartItemId);
            }
            throw e;
        }
    }

    /**
     * CartItemInfo를 CartResponse.CartItemResponse로 변환
     */
    private CartResponse.CartItemResponse toCartItemResponse(CartItemInfo cartItemInfo) {
        // 재고 정보 조회
        Stock stock = stockService.getStock(cartItemInfo.getProductId());
        
        return new CartResponse.CartItemResponse(
            cartItemInfo.getCartItemId(),
            cartItemInfo.getProductId(),
            cartItemInfo.getProductName(),
            cartItemInfo.getPrice().getValue(),
            cartItemInfo.getQuantity(),
            cartItemInfo.getSubtotal().getValue(),
            stock.getAvailableQuantity().getValue(),
            cartItemInfo.getMaxQuantityPerCart()
        );
    }
}