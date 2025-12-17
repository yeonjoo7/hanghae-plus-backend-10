package com.hanghae.ecommerce.infrastructure.kafka.message;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 요청 메시지
 *
 * Kafka를 통해 비동기 쿠폰 발급 요청을 전달하는 메시지 DTO
 */
public class CouponIssuanceRequestMessage {

    private String eventId;
    private Long couponId;
    private Long userId;
    private LocalDateTime requestedAt;

    public CouponIssuanceRequestMessage() {
    }

    public CouponIssuanceRequestMessage(String eventId, Long couponId, Long userId, LocalDateTime requestedAt) {
        this.eventId = eventId;
        this.couponId = couponId;
        this.userId = userId;
        this.requestedAt = requestedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    @Override
    public String toString() {
        return "CouponIssuanceRequestMessage{" +
                "eventId='" + eventId + '\'' +
                ", couponId=" + couponId +
                ", userId=" + userId +
                ", requestedAt=" + requestedAt +
                '}';
    }
}
