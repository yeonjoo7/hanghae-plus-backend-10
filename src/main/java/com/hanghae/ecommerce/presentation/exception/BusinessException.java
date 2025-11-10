package com.hanghae.ecommerce.presentation.exception;

/**
 * 비즈니스 로직 예외의 기본 클래스
 */
public abstract class BusinessException extends RuntimeException {
    
    private final String errorCode;
    private final Object details;
    
    public BusinessException(String errorCode, String message) {
        this(errorCode, message, null);
    }
    
    public BusinessException(String errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Object getDetails() {
        return details;
    }
}