package com.hanghae.ecommerce.infrastructure.kafka.message;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 결과 메시지
 *
 * Kafka를 통해 쿠폰 발급 결과를 전달하는 메시지 DTO
 */
public class CouponIssuanceResultMessage {

    private String eventId;
    private Long couponId;
    private Long userId;
    private String status;  // SUCCESS, ALREADY_ISSUED, EXHAUSTED, FAILED
    private Long userCouponId;
    private String message;
    private LocalDateTime processedAt;

    public CouponIssuanceResultMessage() {
    }

    public CouponIssuanceResultMessage(String eventId, Long couponId, Long userId, String status,
                                       Long userCouponId, String message, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = status;
        this.userCouponId = userCouponId;
        this.message = message;
        this.processedAt = processedAt;
    }

    public static CouponIssuanceResultMessage success(CouponIssuanceRequestMessage request, Long userCouponId) {
        return new CouponIssuanceResultMessage(
                request.getEventId(),
                request.getCouponId(),
                request.getUserId(),
                "SUCCESS",
                userCouponId,
                "쿠폰이 성공적으로 발급되었습니다.",
                LocalDateTime.now()
        );
    }

    public static CouponIssuanceResultMessage alreadyIssued(CouponIssuanceRequestMessage request) {
        return new CouponIssuanceResultMessage(
                request.getEventId(),
                request.getCouponId(),
                request.getUserId(),
                "ALREADY_ISSUED",
                null,
                "이미 발급받은 쿠폰입니다.",
                LocalDateTime.now()
        );
    }

    public static CouponIssuanceResultMessage exhausted(CouponIssuanceRequestMessage request) {
        return new CouponIssuanceResultMessage(
                request.getEventId(),
                request.getCouponId(),
                request.getUserId(),
                "EXHAUSTED",
                null,
                "쿠폰이 모두 소진되었습니다.",
                LocalDateTime.now()
        );
    }

    public static CouponIssuanceResultMessage failed(CouponIssuanceRequestMessage request, String errorMessage) {
        return new CouponIssuanceResultMessage(
                request.getEventId(),
                request.getCouponId(),
                request.getUserId(),
                "FAILED",
                null,
                errorMessage,
                LocalDateTime.now()
        );
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public void setUserCouponId(Long userCouponId) {
        this.userCouponId = userCouponId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    @Override
    public String toString() {
        return "CouponIssuanceResultMessage{" +
                "eventId='" + eventId + '\'' +
                ", couponId=" + couponId +
                ", userId=" + userId +
                ", status='" + status + '\'' +
                ", userCouponId=" + userCouponId +
                ", message='" + message + '\'' +
                ", processedAt=" + processedAt +
                '}';
    }
}
