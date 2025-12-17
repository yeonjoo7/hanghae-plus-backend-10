package com.hanghae.ecommerce.infrastructure.kafka.producer;

import com.hanghae.ecommerce.infrastructure.kafka.message.PaymentCompletedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 결제 이벤트 Kafka Producer
 *
 * 결제 완료 시 데이터 플랫폼으로 전송할 메시지를 Kafka에 발행한다.
 * 트랜잭션이 커밋된 후에만 메시지가 발행되어야 한다.
 */
@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);

    public static final String TOPIC_PAYMENT_COMPLETED = "payment-completed";

    private final KafkaTemplate<String, PaymentCompletedMessage> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, PaymentCompletedMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 결제 완료 메시지를 Kafka로 발행
     *
     * @param message 결제 완료 메시지
     */
    public void sendPaymentCompletedEvent(PaymentCompletedMessage message) {
        log.info("Kafka 메시지 발행 시작: orderId={}, eventId={}",
                message.getOrderId(), message.getEventId());

        CompletableFuture<SendResult<String, PaymentCompletedMessage>> future =
                kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, message.getOrderId(), message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka 메시지 발행 실패: orderId={}, eventId={}, error={}",
                        message.getOrderId(), message.getEventId(), ex.getMessage(), ex);
            } else {
                log.info("Kafka 메시지 발행 성공: orderId={}, eventId={}, topic={}, partition={}, offset={}",
                        message.getOrderId(),
                        message.getEventId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * 동기적으로 메시지 발행 (발행 완료까지 대기)
     *
     * @param message 결제 완료 메시지
     * @return 발행 결과
     */
    public SendResult<String, PaymentCompletedMessage> sendPaymentCompletedEventSync(
            PaymentCompletedMessage message) {
        log.info("Kafka 메시지 동기 발행 시작: orderId={}, eventId={}",
                message.getOrderId(), message.getEventId());

        try {
            SendResult<String, PaymentCompletedMessage> result =
                    kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, message.getOrderId(), message).get();

            log.info("Kafka 메시지 동기 발행 성공: orderId={}, eventId={}, topic={}, partition={}, offset={}",
                    message.getOrderId(),
                    message.getEventId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

            return result;

        } catch (Exception e) {
            log.error("Kafka 메시지 동기 발행 실패: orderId={}, eventId={}, error={}",
                    message.getOrderId(), message.getEventId(), e.getMessage(), e);
            throw new RuntimeException("Kafka 메시지 발행 실패", e);
        }
    }
}
