package com.hanghae.ecommerce.infrastructure.kafka.config;

import com.hanghae.ecommerce.infrastructure.kafka.message.PaymentCompletedMessage;
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
 * Kafka 설정
 *
 * Producer, Consumer, Topic 생성 설정을 관리한다.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ==================== Topic 설정 ====================

    /**
     * 결제 완료 토픽 생성
     * - 파티션 3개: 병렬 처리를 위해
     * - 복제 팩터 1: 로컬 환경 (운영에서는 3 권장)
     */
    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name("payment-completed")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead Letter Queue 토픽 (처리 실패 메시지)
     */
    @Bean
    public NewTopic paymentCompletedDltTopic() {
        return TopicBuilder.name("payment-completed.DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ==================== Producer 설정 ====================

    @Bean
    public ProducerFactory<String, PaymentCompletedMessage> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 신뢰성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // 성능 설정
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, PaymentCompletedMessage> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ==================== Consumer 설정 ====================

    @Bean
    public ConsumerFactory<String, PaymentCompletedMessage> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Consumer Group 설정
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "data-platform-group");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 성능 설정
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // JSON 역직렬화 설정
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.hanghae.ecommerce.*");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentCompletedMessage.class.getName());
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedMessage>
            kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // 수동 오프셋 커밋
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 동시 처리 스레드 수 (파티션 수와 맞춤)
        factory.setConcurrency(3);

        // 에러 핸들러 설정 (재시도 3회 후 DLQ로)
        factory.setCommonErrorHandler(errorHandler());

        return factory;
    }

    /**
     * 에러 핸들러
     * - 1초 간격으로 3회 재시도
     * - 재시도 실패 시 로그 기록 (DLQ 전송은 별도 구현 필요)
     */
    @Bean
    public DefaultErrorHandler errorHandler() {
        // 1000ms 간격으로 3회 재시도
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            // 최종 실패 시 로그 기록
            org.slf4j.LoggerFactory.getLogger(KafkaConfig.class)
                    .error("메시지 처리 최종 실패 (DLQ 전송 필요): topic={}, partition={}, offset={}, error={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            exception.getMessage());
        }, backOff);

        return errorHandler;
    }
}
