package com.hanghae.ecommerce.presentation.controller;

import com.hanghae.ecommerce.application.service.UserService;
import com.hanghae.ecommerce.common.ApiResponse;
import com.hanghae.ecommerce.domain.payment.BalanceTransaction;
import com.hanghae.ecommerce.domain.payment.TransactionType;
import com.hanghae.ecommerce.domain.user.Point;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.presentation.dto.*;
import com.hanghae.ecommerce.presentation.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 잔액 관리 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final UserService userService;

    // TODO: 현재는 임시로 userId를 1L로 고정. 실제로는 인증된 사용자 정보에서 가져와야 함
    private static final Long CURRENT_USER_ID = 1L;

    /**
     * 잔액 충전
     * POST /balance/charge
     */
    @PostMapping("/charge")
    public ApiResponse<ChargeBalanceResponse> chargeBalance(@Valid @RequestBody ChargeBalanceRequest request) {
        try {
            Point chargeAmount = Point.of(request.getAmount());
            BalanceTransaction transaction = userService.chargePoint(CURRENT_USER_ID, chargeAmount);
            
            ChargeBalanceResponse.BalanceInfo balanceInfo = new ChargeBalanceResponse.BalanceInfo(
                transaction.getBalanceBefore().getValue(),
                transaction.getBalanceAfter().getValue()
            );
            
            ChargeBalanceResponse response = new ChargeBalanceResponse(
                transaction.getId(),
                transaction.getTransactionType().name(),
                transaction.getAmount().getValue(),
                balanceInfo,
                transaction.getCreatedAt()
            );
            
            return ApiResponse.success(response, "잔액이 충전되었습니다");
            
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("충전 금액은 1,000원 이상이어야 합니다")) {
                throw new InvalidChargeAmountException(1000, request.getAmount());
            } else if (e.getMessage().contains("사용자를 찾을 수 없습니다")) {
                throw new IllegalArgumentException("사용자를 찾을 수 없습니다");
            }
            throw e;
        }
    }

    /**
     * 잔액 조회
     * GET /balance
     */
    @GetMapping
    public ApiResponse<BalanceResponse> getBalance() {
        User user = userService.getUserById(CURRENT_USER_ID);
        Point balance = userService.getUserBalance(CURRENT_USER_ID);
        
        BalanceResponse response = new BalanceResponse(
            user.getId(),
            balance.getValue(),
            user.getUpdatedAt()
        );
        
        return ApiResponse.success(response);
    }

    /**
     * 잔액 사용 이력 조회
     * GET /balance/history
     */
    @GetMapping("/history")
    public ApiResponse<BalanceHistoryResponse> getBalanceHistory(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String type) {
        
        // 기본값 설정
        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? size : 20;
        
        List<BalanceTransaction> allTransactions = userService.getTransactionHistory(CURRENT_USER_ID);
        
        // 타입 필터링
        if (type != null) {
            TransactionType transactionType = parseTransactionType(type);
            allTransactions = allTransactions.stream()
                    .filter(transaction -> transaction.getTransactionType() == transactionType)
                    .collect(Collectors.toList());
        }
        
        // 페이징 처리 (간단한 구현)
        int totalItems = allTransactions.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalItems);
        
        List<BalanceTransaction> paginatedTransactions = allTransactions.subList(startIndex, endIndex);
        
        List<BalanceHistoryResponse.TransactionResponse> transactions = paginatedTransactions.stream()
                .map(this::toTransactionResponse)
                .collect(Collectors.toList());
        
        BalanceHistoryResponse.Pagination pagination = 
            new BalanceHistoryResponse.Pagination(pageNum, totalPages, totalItems, pageSize);
        
        BalanceHistoryResponse response = new BalanceHistoryResponse(transactions, pagination);
        return ApiResponse.success(response);
    }

    /**
     * BalanceTransaction을 TransactionResponse로 변환
     */
    private BalanceHistoryResponse.TransactionResponse toTransactionResponse(BalanceTransaction transaction) {
        // 거래 금액 (충전은 양수, 결제/환불은 처리에 따라 다름)
        int amount = transaction.getAmount().getValue();
        if (transaction.getTransactionType() == TransactionType.PAYMENT) {
            amount = -amount; // 결제는 음수로 표시
        }
        
        return new BalanceHistoryResponse.TransactionResponse(
            transaction.getId(),
            transaction.getTransactionType().name(),
            amount,
            transaction.getBalanceBefore().getValue(),
            transaction.getBalanceAfter().getValue(),
            transaction.getDescription(),
            transaction.getOrderId(),
            transaction.getCreatedAt()
        );
    }

    /**
     * 거래 유형 문자열을 TransactionType으로 변환
     */
    private TransactionType parseTransactionType(String type) {
        try {
            return TransactionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 거래 유형입니다: " + type);
        }
    }
}