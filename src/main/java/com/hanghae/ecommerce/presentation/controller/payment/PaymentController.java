package com.hanghae.ecommerce.presentation.controller.payment;

import com.hanghae.ecommerce.common.ApiResponse;
import com.hanghae.ecommerce.presentation.dto.payment.PaymentRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/orders")
public class PaymentController {

    @PostMapping("/{orderId}/payment")
    public ApiResponse<Map<String, Object>> processPayment(
            @PathVariable Long orderId,
            @RequestBody PaymentRequest request) {

        // Mock 결제 처리
        Map<String, Object> response = new HashMap<>();
        response.put("paymentId", 1L);
        response.put("orderId", orderId);
        response.put("orderNumber", "ORD-20251031-" + orderId);
        response.put("paymentMethod", request.getPaymentMethod());
        response.put("originalAmount", 3000000);
        response.put("discountAmount", 300000);
        response.put("finalAmount", 2700000);

        // 적용된 쿠폰 목록
        List<Map<String, Object>> appliedCoupons = new ArrayList<>();
        if (request.getCouponIds() != null && !request.getCouponIds().isEmpty()) {
            for (Long couponId : request.getCouponIds()) {
                Map<String, Object> coupon = new HashMap<>();
                coupon.put("couponId", couponId);
                coupon.put("couponName", "할인 쿠폰 " + couponId);
                coupon.put("couponType", "CART");
                coupon.put("discountAmount", 150000);
                appliedCoupons.add(coupon);
            }
        }
        response.put("appliedCoupons", appliedCoupons);

        // 잔액 정보
        Map<String, Integer> balance = new HashMap<>();
        balance.put("before", 5000000);
        balance.put("after", 2300000);
        balance.put("used", 2700000);
        response.put("balance", balance);

        response.put("status", "COMPLETED");
        response.put("paidAt", LocalDateTime.now());

        return ApiResponse.success(response, "결제가 완료되었습니다");
    }
}
