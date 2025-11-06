package com.hanghae.ecommerce.presentation.exception;

import com.hanghae.ecommerce.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기
 * 애플리케이션에서 발생하는 모든 예외를 중앙에서 처리합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception occurred: {}", ex.getMessage(), ex);
        
        HttpStatus status = getHttpStatusFromErrorCode(ex.getErrorCode());
        ApiResponse<Object> response = ApiResponse.error(ex.getErrorCode(), ex.getMessage(), ex.getDetails());
        
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 유효성 검증 실패 예외 처리 (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validation exception occurred: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        ApiResponse<Object> response = ApiResponse.error("VALIDATION_ERROR", "입력값 검증에 실패했습니다", errors);
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * 제약 조건 위반 예외 처리 (@Validated)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("Constraint violation exception occurred: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation ->
            errors.put(violation.getPropertyPath().toString(), violation.getMessage())
        );
        
        ApiResponse<Object> response = ApiResponse.error("VALIDATION_ERROR", "입력값 검증에 실패했습니다", errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * IllegalArgumentException 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument exception occurred: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.error("INVALID_REQUEST", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 일반적인 Exception 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.error("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 에러 코드에 따른 HTTP 상태 코드 매핑
     */
    private HttpStatus getHttpStatusFromErrorCode(String errorCode) {
        switch (errorCode) {
            case "PRODUCT_NOT_FOUND":
            case "CART_ITEM_NOT_FOUND":
            case "ORDER_NOT_FOUND":
            case "COUPON_NOT_FOUND":
                return HttpStatus.NOT_FOUND;
                
            case "INSUFFICIENT_STOCK":
            case "EXCEED_MAX_QUANTITY":
            case "INSUFFICIENT_BALANCE":
            case "INVALID_CHARGE_AMOUNT":
                return HttpStatus.BAD_REQUEST;
                
            case "PAYMENT_ALREADY_COMPLETED":
            case "COUPON_ALREADY_ISSUED":
            case "COUPON_SOLD_OUT":
                return HttpStatus.CONFLICT;
                
            default:
                return HttpStatus.BAD_REQUEST;
        }
    }
}