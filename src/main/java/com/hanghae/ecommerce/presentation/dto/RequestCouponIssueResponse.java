package com.hanghae.ecommerce.presentation.dto;

import lombok.Getter;

/**
 * 쿠폰 발급 요청 응답 DTO (비동기)
 * 
 * Redis 대기열에 추가된 후 즉시 반환되는 응답입니다.
 * 실제 발급은 스케줄러가 비동기로 처리합니다.
 */
@Getter
public class RequestCouponIssueResponse {
    private final Long couponId;
    private final Long queueRank; // 대기열 순위 (1부터 시작)
    private final Long queueSize; // 대기열 전체 크기
    private final String status; // "QUEUED" - 대기열에 추가됨

    public RequestCouponIssueResponse(Long couponId, Long queueRank, Long queueSize) {
        this.couponId = couponId;
        this.queueRank = queueRank;
        this.queueSize = queueSize;
        this.status = "QUEUED";
    }
}

