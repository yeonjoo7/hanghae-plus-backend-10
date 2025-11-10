package com.hanghae.ecommerce.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private ErrorResponse error;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        ErrorResponse error = new ErrorResponse(code, message, null);
        return new ApiResponse<>(false, null, message, error);
    }

    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        ErrorResponse error = new ErrorResponse(code, message, details);
        return new ApiResponse<>(false, null, message, error);
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorResponse {
        private String code;
        private String message;
        private Object details;
    }
}
