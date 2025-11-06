package com.hanghae.ecommerce.balance.controller;

import com.hanghae.ecommerce.balance.dto.ChargeRequest;
import com.hanghae.ecommerce.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/v1/balance")
public class MockBalanceController {

    private final AtomicInteger userBalance = new AtomicInteger(500000);
    private final List<Map<String, Object>> transactions = new ArrayList<>();
    private final AtomicLong transactionIdGenerator = new AtomicLong(1);

    @PostMapping("/charge")
    public ApiResponse<Map<String, Object>> chargeBalance(@RequestBody ChargeRequest request) {
        if (request.getAmount() < 1000) {
            Map<String, Object> details = new HashMap<>();
            details.put("minAmount", 1000);
            details.put("requestedAmount", request.getAmount());
            return ApiResponse.error("INVALID_CHARGE_AMOUNT",
                    "충전 금액은 1,000원 이상이어야 합니다", details);
        }

        int before = userBalance.get();
        int after = userBalance.addAndGet(request.getAmount());

        Long transactionId = transactionIdGenerator.getAndIncrement();

        Map<String, Object> transaction = new HashMap<>();
        transaction.put("transactionId", transactionId);
        transaction.put("type", "CHARGE");
        transaction.put("amount", request.getAmount());

        Map<String, Integer> balance = new HashMap<>();
        balance.put("before", before);
        balance.put("after", after);
        transaction.put("balance", balance);

        transaction.put("createdAt", LocalDateTime.now());

        transactions.add(transaction);

        return ApiResponse.success(transaction, "잔액이 충전되었습니다");
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getBalance() {
        Map<String, Object> response = new HashMap<>();
        response.put("userId", 1L);
        response.put("balance", userBalance.get());
        response.put("lastUpdatedAt", LocalDateTime.now());

        return ApiResponse.success(response);
    }

    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> getBalanceHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {

        List<Map<String, Object>> filteredTransactions = transactions;

        // 타입별 필터링
        if (type != null) {
            filteredTransactions = transactions.stream()
                    .filter(t -> type.equals(t.get("type")))
                    .toList();
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("currentPage", page);
        pagination.put("totalPages", Math.max(1, (filteredTransactions.size() + size - 1) / size));
        pagination.put("totalItems", filteredTransactions.size());
        pagination.put("itemsPerPage", size);

        Map<String, Object> response = new HashMap<>();
        response.put("transactions", filteredTransactions);
        response.put("pagination", pagination);

        return ApiResponse.success(response);
    }
}
