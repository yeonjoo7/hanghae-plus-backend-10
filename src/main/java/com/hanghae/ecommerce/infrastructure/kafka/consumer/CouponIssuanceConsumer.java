package com.hanghae.ecommerce.infrastructure.kafka.consumer;

import com.hanghae.ecommerce.application.coupon.CouponIssuanceProcessor;
import com.hanghae.ecommerce.infrastructure.kafka.message.CouponIssuanceRequestMessage;
import com.hanghae.ecommerce.infrastructure.kafka.producer.CouponIssuanceProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 요청 Kafka Consumer
 *
 * Kafka에서 메시지를 수신하여 Application 레이어로 위임한다.
 */
@Component
public class CouponIssuanceConsumer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssuanceConsumer.class);

    private final CouponIssuanceProcessor couponIssuanceProcessor;

    public CouponIssuanceConsumer(CouponIssuanceProcessor couponIssuanceProcessor) {
        this.couponIssuanceProcessor = couponIssuanceProcessor;
    }

    @KafkaListener(
            topics = CouponIssuanceProducer.COUPON_ISSUANCE_REQUEST_TOPIC,
            groupId = "coupon-issuer-group",
            containerFactory = "couponKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, CouponIssuanceRequestMessage> record,
                        Acknowledgment ack) {

        CouponIssuanceRequestMessage request = record.value();
        log.debug("쿠폰 발급 요청 수신: eventId={}, partition={}, offset={}",
                request.getEventId(), record.partition(), record.offset());

        boolean success = couponIssuanceProcessor.process(request);

        if (success) {
            ack.acknowledge();
        } else {
            // 재시도를 위해 예외 발생 (ErrorHandler가 처리)
            throw new RuntimeException("쿠폰 발급 처리 실패: eventId=" + request.getEventId());
        }
    }
}
