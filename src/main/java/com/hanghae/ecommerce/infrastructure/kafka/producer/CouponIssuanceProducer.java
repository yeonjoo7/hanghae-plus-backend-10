package com.hanghae.ecommerce.infrastructure.kafka.producer;

import com.hanghae.ecommerce.infrastructure.kafka.message.CouponIssuanceRequestMessage;
import com.hanghae.ecommerce.infrastructure.kafka.message.CouponIssuanceResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 쿠폰 발급 관련 Kafka Producer
 *
 * 쿠폰 발급 요청 및 결과 메시지를 Kafka로 발행한다.
 */
@Component
public class CouponIssuanceProducer {

    private static final Logger log = LoggerFactory.getLogger(CouponIssuanceProducer.class);

    public static final String COUPON_ISSUANCE_REQUEST_TOPIC = "coupon-issuance-request";
    public static final String COUPON_ISSUANCE_RESULT_TOPIC = "coupon-issuance-result";

    private final KafkaTemplate<String, Object> couponKafkaTemplate;

    public CouponIssuanceProducer(KafkaTemplate<String, Object> couponKafkaTemplate) {
        this.couponKafkaTemplate = couponKafkaTemplate;
    }

    /**
     * 쿠폰 발급 요청 메시지 발행
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     * @return 발행된 메시지의 eventId
     */
    public String requestCouponIssuance(Long couponId, Long userId) {
        String eventId = UUID.randomUUID().toString();
        CouponIssuanceRequestMessage message = new CouponIssuanceRequestMessage(
                eventId,
                couponId,
                userId,
                LocalDateTime.now()
        );

        // couponId를 파티션 키로 사용하여 동일 쿠폰은 같은 파티션으로
        String partitionKey = String.valueOf(couponId);

        couponKafkaTemplate.send(COUPON_ISSUANCE_REQUEST_TOPIC, partitionKey, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("쿠폰 발급 요청 발행 실패: couponId={}, userId={}, eventId={}",
                                couponId, userId, eventId, ex);
                    } else {
                        log.info("쿠폰 발급 요청 발행 성공: partition={}, offset={}, eventId={}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                eventId);
                    }
                });

        return eventId;
    }

    /**
     * 쿠폰 발급 결과 메시지 발행
     *
     * @param resultMessage 발급 결과 메시지
     */
    public void sendIssuanceResult(CouponIssuanceResultMessage resultMessage) {
        String partitionKey = String.valueOf(resultMessage.getUserId());

        couponKafkaTemplate.send(COUPON_ISSUANCE_RESULT_TOPIC, partitionKey, resultMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("쿠폰 발급 결과 발행 실패: eventId={}, status={}",
                                resultMessage.getEventId(), resultMessage.getStatus(), ex);
                    } else {
                        log.info("쿠폰 발급 결과 발행 성공: eventId={}, status={}, partition={}, offset={}",
                                resultMessage.getEventId(),
                                resultMessage.getStatus(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
