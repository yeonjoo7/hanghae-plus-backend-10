package com.hanghae.ecommerce.infrastructure.kafka.message;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka로 전송되는 결제 완료 메시지
 *
 * 이 메시지는 결제가 완료(커밋)된 후 발행되어
 * 외부 데이터 플랫폼으로 전송된다.
 */
public class PaymentCompletedMessage {

    private String eventId;
    private String orderId;
    private String userId;
    private String orderNumber;
    private long totalAmount;
    private long discountAmount;
    private long finalAmount;
    private String paymentMethod;
    private Map<Long, Integer> productOrderCounts;
    private LocalDateTime paidAt;
    private LocalDateTime publishedAt;

    public PaymentCompletedMessage() {
    }

    public PaymentCompletedMessage(String orderId, String userId, String orderNumber,
            long totalAmount, long discountAmount, long finalAmount,
            String paymentMethod, Map<Long, Integer> productOrderCounts,
            LocalDateTime paidAt) {
        this.eventId = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.userId = userId;
        this.orderNumber = orderNumber;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.paymentMethod = paymentMethod;
        this.productOrderCounts = productOrderCounts;
        this.paidAt = paidAt;
        this.publishedAt = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(long discountAmount) {
        this.discountAmount = discountAmount;
    }

    public long getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(long finalAmount) {
        this.finalAmount = finalAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Map<Long, Integer> getProductOrderCounts() {
        return productOrderCounts;
    }

    public void setProductOrderCounts(Map<Long, Integer> productOrderCounts) {
        this.productOrderCounts = productOrderCounts;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    @Override
    public String toString() {
        return "PaymentCompletedMessage{" +
                "eventId='" + eventId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", userId='" + userId + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", totalAmount=" + totalAmount +
                ", discountAmount=" + discountAmount +
                ", finalAmount=" + finalAmount +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", paidAt=" + paidAt +
                ", publishedAt=" + publishedAt +
                '}';
    }
}
