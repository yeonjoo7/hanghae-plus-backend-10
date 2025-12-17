package com.hanghae.ecommerce.infrastructure.kafka.config;

import com.hanghae.ecommerce.infrastructure.kafka.message.CouponIssuanceRequestMessage;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * 쿠폰 발급용 Kafka 설정
 *
 * 쿠폰 발급 요청/결과 토픽과 관련 Producer/Consumer 설정을 관리한다.
 */
@Configuration
public class CouponKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ==================== Topic 설정 ====================

    /**
     * 쿠폰 발급 요청 토픽
     * - 파티션 3개: 파티션은 줄일 수 없으므로 적정 수준으로 유지
     * - couponId를 파티션 키로 사용하여 해시 기반 분산
     * - 복제 팩터 1: 로컬 환경 (운영에서는 3 권장)
     */
    @Bean
    public NewTopic couponIssuanceRequestTopic() {
        return TopicBuilder.name("coupon-issuance-request")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 쿠폰 발급 결과 토픽
     */
    @Bean
    public NewTopic couponIssuanceResultTopic() {
        return TopicBuilder.name("coupon-issuance-result")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 쿠폰 발급 Dead Letter Queue 토픽
     */
    @Bean
    public NewTopic couponIssuanceDltTopic() {
        return TopicBuilder.name("coupon-issuance-request.DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ==================== Producer 설정 ====================

    @Bean
    public ProducerFactory<String, Object> couponProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 신뢰성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> couponKafkaTemplate() {
        return new KafkaTemplate<>(couponProducerFactory());
    }

    // ==================== Consumer 설정 ====================

    @Bean
    public ConsumerFactory<String, CouponIssuanceRequestMessage> couponConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Consumer Group 설정
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "coupon-issuer-group");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 성능 설정
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // JSON 역직렬화 설정
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.hanghae.ecommerce.*");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CouponIssuanceRequestMessage.class.getName());
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssuanceRequestMessage>
            couponKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, CouponIssuanceRequestMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(couponConsumerFactory());

        // 수동 오프셋 커밋
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 동시 처리 스레드 수 (파티션 수와 맞춤)
        factory.setConcurrency(3);

        // 에러 핸들러 설정
        factory.setCommonErrorHandler(couponErrorHandler());

        return factory;
    }

    /**
     * 쿠폰 발급용 에러 핸들러
     * - 1초 간격으로 3회 재시도
     */
    @Bean
    public DefaultErrorHandler couponErrorHandler() {
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            org.slf4j.LoggerFactory.getLogger(CouponKafkaConfig.class)
                    .error("쿠폰 발급 메시지 처리 최종 실패 (DLQ 전송 필요): topic={}, partition={}, offset={}, error={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            exception.getMessage());
        }, backOff);

        return errorHandler;
    }
}
