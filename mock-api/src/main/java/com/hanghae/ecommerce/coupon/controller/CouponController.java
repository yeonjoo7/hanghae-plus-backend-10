package com.hanghae.ecommerce.coupon.controller;

import com.hanghae.ecommerce.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/v1/coupons")
public class CouponController {

    private final Map<Long, Map<String, Object>> userCoupons = new HashMap<>();
    private final AtomicLong userCouponIdGenerator = new AtomicLong(1);

    @PostMapping("/{couponId}/issue")
    public ApiResponse<Map<String, Object>> issueCoupon(@PathVariable Long couponId) {
        Long userCouponId = userCouponIdGenerator.getAndIncrement();

        Map<String, Object> coupon = new HashMap<>();
        coupon.put("userCouponId", userCouponId);
        coupon.put("couponId", couponId);
        coupon.put("couponName", "신규 가입 10% 할인");
        coupon.put("couponType", "CART");
        coupon.put("discountType", "PERCENTAGE");
        coupon.put("discountValue", 10);
        coupon.put("minOrderAmount", 50000);
        coupon.put("expiresAt", LocalDateTime.now().plusMonths(2));
        coupon.put("issuedAt", LocalDateTime.now());
        coupon.put("status", "AVAILABLE");

        userCoupons.put(userCouponId, coupon);

        return ApiResponse.success(coupon, "쿠폰이 발급되었습니다");
    }

    @GetMapping("/my")
    public ApiResponse<Map<String, Object>> getMyCoupons(
            @RequestParam(required = false) String status) {

        List<Map<String, Object>> coupons = new ArrayList<>(userCoupons.values());

        // 상태별 필터링
        if (status != null) {
            coupons = coupons.stream()
                    .filter(c -> status.equals(c.get("status")))
                    .toList();
        }

        long availableCount = coupons.stream()
                .filter(c -> "AVAILABLE".equals(c.get("status")))
                .count();

        Map<String, Object> response = new HashMap<>();
        response.put("coupons", coupons);
        response.put("totalCount", coupons.size());
        response.put("availableCount", availableCount);

        return ApiResponse.success(response);
    }

    @GetMapping("/usage-history")
    public ApiResponse<Map<String, Object>> getCouponUsageHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<Map<String, Object>> usageHistory = new ArrayList<>();

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("currentPage", page);
        pagination.put("totalPages", 1);
        pagination.put("totalItems", usageHistory.size());
        pagination.put("itemsPerPage", size);

        Map<String, Object> response = new HashMap<>();
        response.put("usageHistory", usageHistory);
        response.put("pagination", pagination);

        return ApiResponse.success(response);
    }
}
