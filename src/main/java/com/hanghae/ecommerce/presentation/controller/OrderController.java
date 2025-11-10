package com.hanghae.ecommerce.presentation.controller;

import com.hanghae.ecommerce.application.service.OrderService;
import com.hanghae.ecommerce.application.service.OrderService.OrderInfo;
import com.hanghae.ecommerce.application.service.OrderService.OrderSummary;
import com.hanghae.ecommerce.application.service.OrderService.OrderItemInfo;
import com.hanghae.ecommerce.common.ApiResponse;
import com.hanghae.ecommerce.domain.order.OrderState;
import com.hanghae.ecommerce.presentation.dto.*;
import com.hanghae.ecommerce.presentation.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // TODO: 현재는 임시로 userId를 1L로 고정. 실제로는 인증된 사용자 정보에서 가져와야 함
    private static final Long CURRENT_USER_ID = 1L;

    /**
     * 주문 생성
     * POST /orders
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        try {
            OrderInfo orderInfo = orderService.createOrder(
                CURRENT_USER_ID,
                request.getCartItemIds(),
                request.getShippingAddress().getRecipientName(),
                request.getShippingAddress().getPhone(),
                request.getShippingAddress().getZipCode(),
                request.getShippingAddress().getAddress(),
                request.getShippingAddress().getDetailAddress()
            );
            
            CreateOrderResponse response = toCreateOrderResponse(orderInfo);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("재고가 부족한 상품이 있습니다")) {
                // 실제로는 부족한 상품들의 상세 정보를 파싱해서 응답해야 함
                throw new InsufficientStockException(10, 5); // 예시 값
            } else if (e.getMessage().contains("사용자를 찾을 수 없습니다")) {
                throw new IllegalArgumentException("사용자를 찾을 수 없습니다");
            }
            throw e;
        }
    }

    /**
     * 주문 상세 조회
     * GET /orders/{orderId}
     */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrder(@PathVariable Long orderId) {
        try {
            OrderInfo orderInfo = orderService.getOrder(CURRENT_USER_ID, orderId);
            OrderDetailResponse response = toOrderDetailResponse(orderInfo);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("주문을 찾을 수 없습니다")) {
                throw new OrderNotFoundException(orderId);
            } else if (e.getMessage().contains("본인의 주문이 아닙니다")) {
                throw new IllegalArgumentException("본인의 주문이 아닙니다");
            }
            throw e;
        }
    }

    /**
     * 주문 목록 조회
     * GET /orders
     */
    @GetMapping
    public ApiResponse<OrderListResponse> getOrders(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        // 기본값 설정
        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? size : 20;
        
        // 날짜 파싱
        LocalDateTime start = null;
        LocalDateTime end = null;
        if (startDate != null) {
            start = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
        }
        if (endDate != null) {
            end = LocalDate.parse(endDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atTime(23, 59, 59);
        }
        
        // 상태 파싱
        OrderState orderState = null;
        if (status != null) {
            orderState = parseOrderState(status);
        }
        
        List<OrderSummary> orders = orderService.getUserOrdersWithFilter(
            CURRENT_USER_ID, orderState, start, end);
        
        // 페이징 처리 (간단한 구현)
        int totalItems = orders.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalItems);
        
        List<OrderSummary> paginatedOrders = orders.subList(startIndex, endIndex);
        
        List<OrderListResponse.OrderSummaryResponse> orderSummaries = paginatedOrders.stream()
                .map(this::toOrderSummaryResponse)
                .collect(Collectors.toList());
        
        OrderListResponse.Pagination pagination = new OrderListResponse.Pagination(
            pageNum, totalPages, totalItems, pageSize
        );
        
        OrderListResponse response = new OrderListResponse(orderSummaries, pagination);
        return ApiResponse.success(response);
    }

    /**
     * CreateOrderResponse 변환
     */
    private CreateOrderResponse toCreateOrderResponse(OrderInfo orderInfo) {
        List<CreateOrderResponse.OrderItemResponse> items = orderInfo.getOrderItems().stream()
                .map(item -> new CreateOrderResponse.OrderItemResponse(
                    item.getOrderItemId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getPrice().getValue(),
                    item.getQuantity(),
                    item.getSubtotal().getValue()
                ))
                .collect(Collectors.toList());
        
        CreateOrderResponse.ShippingAddressResponse shippingAddress = 
            new CreateOrderResponse.ShippingAddressResponse(
                orderInfo.getOrder().getRecipient().getName(),
                orderInfo.getOrder().getRecipient().getPhone(),
                orderInfo.getOrder().getAddress().getZipCode(),
                orderInfo.getOrder().getAddress().getAddress(),
                orderInfo.getOrder().getAddress().getDetailAddress()
            );
        
        return new CreateOrderResponse(
            orderInfo.getOrderId(),
            orderInfo.getOrderNumber(),
            mapOrderStatus(orderInfo.getStatus()),
            items,
            orderInfo.getTotalAmount().getValue(),
            shippingAddress,
            orderInfo.getOrder().getCreatedAt()
        );
    }

    /**
     * OrderDetailResponse 변환
     */
    private OrderDetailResponse toOrderDetailResponse(OrderInfo orderInfo) {
        List<OrderDetailResponse.OrderItemResponse> items = orderInfo.getOrderItems().stream()
                .map(item -> new OrderDetailResponse.OrderItemResponse(
                    item.getOrderItemId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getPrice().getValue(),
                    item.getQuantity(),
                    item.getSubtotal().getValue()
                ))
                .collect(Collectors.toList());
        
        // 결제 정보 (현재는 null로 처리, 결제 완료 후 추가될 예정)
        OrderDetailResponse.PaymentResponse payment = null;
        
        // 적용된 쿠폰 (현재는 빈 리스트로 처리)
        List<OrderDetailResponse.AppliedCouponResponse> appliedCoupons = List.of();
        
        OrderDetailResponse.ShippingAddressResponse shippingAddress = 
            new OrderDetailResponse.ShippingAddressResponse(
                orderInfo.getOrder().getRecipient().getName(),
                orderInfo.getOrder().getRecipient().getPhone(),
                orderInfo.getOrder().getAddress().getZipCode(),
                orderInfo.getOrder().getAddress().getAddress(),
                orderInfo.getOrder().getAddress().getDetailAddress()
            );
        
        return new OrderDetailResponse(
            orderInfo.getOrderId(),
            orderInfo.getOrderNumber(),
            orderInfo.getOrder().getUserId(),
            mapOrderStatus(orderInfo.getStatus()),
            items,
            payment,
            appliedCoupons,
            shippingAddress,
            orderInfo.getOrder().getCreatedAt(),
            orderInfo.getOrder().getUpdatedAt()
        );
    }

    /**
     * OrderSummaryResponse 변환
     */
    private OrderListResponse.OrderSummaryResponse toOrderSummaryResponse(OrderSummary orderSummary) {
        return new OrderListResponse.OrderSummaryResponse(
            orderSummary.getOrderId(),
            orderSummary.getOrderNumber(),
            mapOrderStatus(orderSummary.getStatus()),
            orderSummary.getTotalAmount().getValue(),
            orderSummary.getDiscountAmount().getValue(),
            orderSummary.getFinalAmount().getValue(),
            orderSummary.getItemCount(),
            orderSummary.getCreatedAt()
        );
    }

    /**
     * 주문 상태 문자열을 OrderState로 변환
     */
    private OrderState parseOrderState(String status) {
        switch (status.toUpperCase()) {
            case "PENDING_PAYMENT":
                return OrderState.PENDING_PAYMENT;
            case "COMPLETED":
                return OrderState.COMPLETED;
            case "CANCELLED":
                return OrderState.CANCELLED;
            case "REFUNDED":
                return OrderState.REFUNDED;
            default:
                throw new IllegalArgumentException("유효하지 않은 주문 상태입니다: " + status);
        }
    }

    /**
     * OrderState를 API 응답용 문자열로 변환
     */
    private String mapOrderStatus(OrderState state) {
        switch (state) {
            case PENDING_PAYMENT:
                return "PENDING_PAYMENT";
            case COMPLETED:
                return "COMPLETED";
            case CANCELLED:
                return "CANCELLED";
            case REFUNDED:
                return "REFUNDED";
            default:
                return state.name();
        }
    }
}