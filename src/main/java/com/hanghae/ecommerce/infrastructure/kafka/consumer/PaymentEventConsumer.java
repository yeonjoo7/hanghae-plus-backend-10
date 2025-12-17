package com.hanghae.ecommerce.infrastructure.kafka.consumer;

import com.hanghae.ecommerce.infrastructure.external.DataTransmissionService;
import com.hanghae.ecommerce.infrastructure.kafka.message.PaymentCompletedMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 결제 이벤트 Kafka Consumer
 *
 * Kafka에서 결제 완료 메시지를 소비하여 외부 데이터 플랫폼으로 전송한다.
 * Consumer Group: data-platform-group
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final DataTransmissionService dataTransmissionService;

    public PaymentEventConsumer(DataTransmissionService dataTransmissionService) {
        this.dataTransmissionService = dataTransmissionService;
    }

    /**
     * 결제 완료 메시지를 소비하여 외부 데이터 플랫폼으로 전송
     *
     * @param record Kafka 메시지 레코드
     * @param ack 수동 오프셋 커밋용 Acknowledgment
     */
    @KafkaListener(
            topics = "payment-completed",
            groupId = "data-platform-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentCompleted(
            ConsumerRecord<String, PaymentCompletedMessage> record,
            Acknowledgment ack) {

        PaymentCompletedMessage message = record.value();

        log.info("Kafka 메시지 수신: topic={}, partition={}, offset={}, orderId={}, eventId={}",
                record.topic(),
                record.partition(),
                record.offset(),
                message.getOrderId(),
                message.getEventId());

        try {
            // 외부 데이터 플랫폼으로 전송
            sendToDataPlatform(message);

            // 처리 성공 시 오프셋 커밋
            ack.acknowledge();

            log.info("메시지 처리 완료 및 오프셋 커밋: orderId={}, eventId={}",
                    message.getOrderId(), message.getEventId());

        } catch (Exception e) {
            log.error("메시지 처리 실패: orderId={}, eventId={}, error={}",
                    message.getOrderId(), message.getEventId(), e.getMessage(), e);

            // 처리 실패 시 오프셋 커밋하지 않음 -> 재시도
            // 재시도 횟수 초과 시 DLQ로 전송됨 (ErrorHandler에서 처리)
            throw new RuntimeException("메시지 처리 실패", e);
        }
    }

    /**
     * 외부 데이터 플랫폼으로 주문 데이터 전송
     */
    private void sendToDataPlatform(PaymentCompletedMessage message) {
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("eventId", message.getEventId());
        orderData.put("orderId", message.getOrderId());
        orderData.put("userId", message.getUserId());
        orderData.put("orderNumber", message.getOrderNumber());
        orderData.put("totalAmount", message.getTotalAmount());
        orderData.put("discountAmount", message.getDiscountAmount());
        orderData.put("finalAmount", message.getFinalAmount());
        orderData.put("paymentMethod", message.getPaymentMethod());
        orderData.put("timestamp", message.getPaidAt());
        orderData.put("publishedAt", message.getPublishedAt());

        dataTransmissionService.send(orderData);

        log.info("데이터 플랫폼 전송 완료: orderId={}, eventId={}",
                message.getOrderId(), message.getEventId());
    }
}
