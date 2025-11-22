package com.hanghae.ecommerce.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DataTransmissionService {

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final String apiUrl;
    private final String apiKey;

    public DataTransmissionService(RestTemplateBuilder restTemplateBuilder,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;

        this.apiUrl = System.getenv("DATA_PLATFORM_URL") != null
                ? System.getenv("DATA_PLATFORM_URL")
                : "http://localhost:4000";

        this.apiKey = System.getenv("DATA_PLATFORM_API_KEY") != null
                ? System.getenv("DATA_PLATFORM_API_KEY")
                : "test-key";
    }

    /**
     * 외부 데이터 플랫폼으로 주문 데이터를 전송
     */
    public Map<String, Object> send(Map<String, Object> orderData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderData, headers);

            // 외부 API POST 요청
            org.springframework.http.ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                    apiUrl + "/api/orders",
                    org.springframework.http.HttpMethod.POST,
                    request,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return responseEntity.getBody();

        } catch (Exception e) {
            // 실패 시 Outbox 테이블에 저장
            saveToOutbox("ORDER", (String) orderData.get("orderId"), "ORDER_CREATED", orderData);
            throw new RuntimeException("데이터 전송 실패: Outbox에 저장됨", e);
        }
    }

    /**
     * Outbox 테이블에 실패 기록 저장
     */
    public void saveToOutbox(String aggregateType, String aggregateId, String eventType, Map<String, Object> data) {
        try {
            String payload = objectMapper.writeValueAsString(data);

            jdbcTemplate.update(
                    """
                            INSERT INTO data_transmissions
                            (id, aggregate_type, aggregate_id, event_type, payload, status, attempts)
                            VALUES (?, ?, ?, ?, ?, 'PENDING', 0)
                            """,
                    UUID.randomUUID().toString(),
                    aggregateType,
                    aggregateId,
                    eventType,
                    payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save to outbox", e);
        }
    }

    /**
     * Outbox에 남은 PENDING 데이터 재시도 처리
     */
    public void retryPendingTransmissions() {
        List<Map<String, Object>> pending = jdbcTemplate.queryForList(
                """
                        SELECT * FROM data_transmissions
                        WHERE state = 'PENDING' AND attempts < 3
                        ORDER BY created_at
                        LIMIT 10
                        """);

        for (Map<String, Object> transmission : pending) {
            String id = (String) transmission.get("id");
            String payload = (String) transmission.get("payload");

            try {
                // JSON 문자열을 Map으로 파싱
                Map<String, Object> orderData = objectMapper.readValue(payload,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });

                // 재시도
                send(orderData);

                // 성공 시 상태 업데이트
                jdbcTemplate.update(
                        "UPDATE data_transmissions SET state = 'SUCCESS', sent_at = NOW() WHERE id = ?",
                        id);

            } catch (Exception e) {
                // 실패 시 재시도 횟수 증가
                jdbcTemplate.update(
                        "UPDATE data_transmissions SET attempts = attempts + 1, error_message = ? WHERE id = ?",
                        e.getMessage(), id);

                // 3회 실패 시 FAILED 마킹
                Integer attempts = (Integer) transmission.get("attempts");
                if (attempts != null && attempts >= 2) {
                    jdbcTemplate.update(
                            "UPDATE data_transmissions SET state = 'FAILED', failed_at = NOW() WHERE id = ?",
                            id);
                }
            }
        }
    }

    /**
     * 특정 이벤트 타입의 전송 내역 조회
     */
    public List<Map<String, Object>> getTransmissionHistory(String eventType, String status) {
        String sql = """
                SELECT * FROM data_transmissions
                WHERE event_type = ? AND status = ?
                ORDER BY created_at DESC
                LIMIT 100
                """;

        return jdbcTemplate.queryForList(sql, eventType, status);
    }
}