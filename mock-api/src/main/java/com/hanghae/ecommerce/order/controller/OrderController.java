package com.hanghae.ecommerce.order.controller;

import com.hanghae.ecommerce.common.ApiResponse;
import com.hanghae.ecommerce.order.dto.OrderRequest;
import com.hanghae.ecommerce.order.dto.OrderResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private final Map<Long, OrderResponse> orders = new HashMap<>();
    private final AtomicLong orderIdGenerator = new AtomicLong(1001);

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        if (request.getCartItemIds() == null || request.getCartItemIds().isEmpty()) {
            return ApiResponse.error("CART_EMPTY", "장바구니가 비어있습니다");
        }

        Long orderId = orderIdGenerator.getAndIncrement();
        String orderNumber = "ORD-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) +
                "-" + orderId;

        List<OrderResponse.OrderItem> orderItems = new ArrayList<>();
        for (int i = 0; i < request.getCartItemIds().size(); i++) {
            OrderResponse.OrderItem item = OrderResponse.OrderItem.builder()
                    .orderItemId((long) (i + 1))
                    .productId((long) (i + 1))
                    .productName("상품 " + (i + 1))
                    .price(1500000)
                    .quantity(2)
                    .subtotal(3000000)
                    .build();
            orderItems.add(item);
        }

        int totalAmount = orderItems.stream()
                .mapToInt(OrderResponse.OrderItem::getSubtotal)
                .sum();

        OrderResponse order = OrderResponse.builder()
                .orderId(orderId)
                .orderNumber(orderNumber)
                .userId(1L)
                .status("PENDING_PAYMENT")
                .orderItems(orderItems)
                .totalAmount(totalAmount)
                .shippingAddress(request.getShippingAddress())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        orders.put(orderId, order);

        return ApiResponse.success(order);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long orderId) {
        OrderResponse order = orders.get(orderId);
        if (order == null) {
            return ApiResponse.error("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다");
        }
        return ApiResponse.success(order);
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<Map<String, Object>> orderList = new ArrayList<>();
        for (OrderResponse order : orders.values()) {
            Map<String, Object> orderSummary = new HashMap<>();
            orderSummary.put("orderId", order.getOrderId());
            orderSummary.put("orderNumber", order.getOrderNumber());
            orderSummary.put("status", order.getStatus());
            orderSummary.put("totalAmount", order.getTotalAmount());
            orderSummary.put("itemCount", order.getOrderItems().size());
            orderSummary.put("createdAt", order.getCreatedAt());
            orderList.add(orderSummary);
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("currentPage", page);
        pagination.put("totalPages", 1);
        pagination.put("totalItems", orderList.size());
        pagination.put("itemsPerPage", size);

        Map<String, Object> response = new HashMap<>();
        response.put("orders", orderList);
        response.put("pagination", pagination);

        return ApiResponse.success(response);
    }
}
