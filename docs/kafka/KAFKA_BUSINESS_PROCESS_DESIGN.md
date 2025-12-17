# 카프카를 활용한 비즈니스 프로세스 개선 설계

## 목차
1. [현재 아키텍처 분석](#1-현재-아키텍처-분석)
2. [개선된 아키텍처](#2-개선된-아키텍처)
3. [시퀀스 다이어그램](#3-시퀀스-다이어그램)
4. [Kafka 토픽 설계](#4-kafka-토픽-설계)
5. [구현 상세](#5-구현-상세)
6. [장애 대응 및 복구 전략](#6-장애-대응-및-복구-전략)
7. [모니터링 및 운영](#7-모니터링-및-운영)

---

## 1. 현재 아키텍처 분석

### 1.1 기존 데이터 전송 방식

기존에는 결제 완료 후 동기적으로 외부 데이터 플랫폼에 REST API를 호출하는 방식이었습니다.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          기존 아키텍처 (동기 방식)                           │
└────────────────────────────────────────────────────────────────────────────┘

┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│    Client    │───▶│   Payment    │───▶│   Event      │───▶│   Data       │
│              │    │   Service    │    │   Handler    │    │   Platform   │
└──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
                           │                   │                    │
                           │                   │                    │
                    ┌──────▼──────┐     ┌──────▼──────┐      ┌──────▼──────┐
                    │   MySQL     │     │   Redis     │      │  Mock API   │
                    │ (결제 저장)  │     │ (랭킹 갱신) │      │ (REST 호출) │
                    └─────────────┘     └─────────────┘      └─────────────┘
```

### 1.2 기존 방식의 문제점

| 문제점 | 설명 |
|--------|------|
| **강한 결합** | 결제 서비스가 데이터 플랫폼에 직접 의존 |
| **장애 전파** | 데이터 플랫폼 장애 시 결제 처리에 영향 |
| **재시도 복잡성** | 실패 시 Outbox 패턴으로 재시도, 관리 복잡 |
| **확장성 제한** | 새로운 Consumer 추가 시 코드 수정 필요 |
| **처리량 제한** | 동기 호출로 인한 처리량 병목 |

---

## 2. 개선된 아키텍처

### 2.1 Kafka 기반 이벤트 드리븐 아키텍처

```
┌────────────────────────────────────────────────────────────────────────────┐
│                       개선된 아키텍처 (Kafka 기반)                           │
└────────────────────────────────────────────────────────────────────────────┘

┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│    Client    │───▶│   Payment    │───▶│   Event      │
│              │    │   Service    │    │   Handler    │
└──────────────┘    └──────────────┘    └──────────────┘
                           │                   │
                    ┌──────▼──────┐            │
                    │   MySQL     │            │
                    │ (결제 저장)  │            │
                    └─────────────┘            │
                                               │
                                               ▼
                    ┌──────────────────────────────────────────────────┐
                    │                 Kafka Cluster                    │
                    │  ┌─────────────────────────────────────────────┐ │
                    │  │     Topic: payment-completed                │ │
                    │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐       │ │
                    │  │  │ Part 0  │ │ Part 1  │ │ Part 2  │       │ │
                    │  │  └─────────┘ └─────────┘ └─────────┘       │ │
                    │  └─────────────────────────────────────────────┘ │
                    └──────────────────────────────────────────────────┘
                           │              │              │
                           ▼              ▼              ▼
                    ┌─────────────────────────────────────────────────┐
                    │           Consumer Group: data-platform-group   │
                    │  ┌─────────────┐ ┌─────────────┐ ┌───────────┐ │
                    │  │ Consumer 1  │ │ Consumer 2  │ │Consumer 3 │ │
                    │  └──────┬──────┘ └──────┬──────┘ └─────┬─────┘ │
                    └─────────┼───────────────┼──────────────┼───────┘
                              │               │              │
                              ▼               ▼              ▼
                    ┌─────────────────────────────────────────────────┐
                    │              Data Platform (Mock API)           │
                    └─────────────────────────────────────────────────┘
```

### 2.2 개선된 아키텍처의 장점

| 장점 | 설명 |
|------|------|
| **느슨한 결합** | Producer와 Consumer가 독립적으로 동작 |
| **장애 격리** | 데이터 플랫폼 장애가 결제에 영향 없음 |
| **자동 재시도** | Kafka의 내장 재시도 메커니즘 활용 |
| **확장성** | Consumer Group으로 수평 확장 용이 |
| **높은 처리량** | 비동기 처리로 병목 해소 |
| **이벤트 재생** | 메시지 보존으로 재처리 가능 |

---

## 3. 시퀀스 다이어그램

### 3.1 결제 완료 및 Kafka 메시지 발행

```
┌──────┐  ┌──────────────┐  ┌───────┐  ┌─────────────┐  ┌───────┐  ┌────────────┐
│Client│  │PaymentService│  │ MySQL │  │EventHandler │  │ Kafka │  │DataPlatform│
└──┬───┘  └──────┬───────┘  └───┬───┘  └──────┬──────┘  └───┬───┘  └─────┬──────┘
   │             │              │             │             │            │
   │ POST /orders/{id}/payment  │             │             │            │
   │────────────▶│              │             │             │            │
   │             │              │             │             │            │
   │             │ BEGIN TRANSACTION          │             │            │
   │             │─────────────▶│             │             │            │
   │             │              │             │             │            │
   │             │ 잔액 차감    │             │             │            │
   │             │─────────────▶│             │             │            │
   │             │              │             │             │            │
   │             │ 재고 차감    │             │             │            │
   │             │─────────────▶│             │             │            │
   │             │              │             │             │            │
   │             │ 결제 정보 저장              │             │            │
   │             │─────────────▶│             │             │            │
   │             │              │             │             │            │
   │             │ 이벤트 발행 (ApplicationEvent)            │            │
   │             │─────────────────────────▶│             │            │
   │             │              │             │             │            │
   │             │ COMMIT       │             │             │            │
   │             │─────────────▶│             │             │            │
   │             │              │             │             │            │
   │◀────────────│ 결제 완료 응답             │             │            │
   │             │              │             │             │            │
   │             │              │  @TransactionalEventListener          │
   │             │              │  (AFTER_COMMIT)                       │
   │             │              │             │             │            │
   │             │              │             │ Kafka 메시지 발행        │
   │             │              │             │────────────▶│            │
   │             │              │             │             │            │
   │             │              │             │◀── ACK ─────│            │
   │             │              │             │             │            │
```

### 3.2 Kafka Consumer의 메시지 처리

```
┌───────┐  ┌──────────────────┐  ┌────────────────┐  ┌────────────┐
│ Kafka │  │PaymentConsumer   │  │DataTransmission│  │DataPlatform│
└───┬───┘  └────────┬─────────┘  └───────┬────────┘  └─────┬──────┘
    │               │                    │                 │
    │ poll()        │                    │                 │
    │──────────────▶│                    │                 │
    │               │                    │                 │
    │ ConsumerRecord│                    │                 │
    │──────────────▶│                    │                 │
    │               │                    │                 │
    │               │ 메시지 역직렬화    │                 │
    │               │────────────────────│                 │
    │               │                    │                 │
    │               │ send(orderData)    │                 │
    │               │───────────────────▶│                 │
    │               │                    │                 │
    │               │                    │ POST /api/orders│
    │               │                    │────────────────▶│
    │               │                    │                 │
    │               │                    │◀─── 200 OK ─────│
    │               │                    │                 │
    │               │◀── 전송 완료 ──────│                 │
    │               │                    │                 │
    │ ack.acknowledge()                  │                 │
    │◀──────────────│                    │                 │
    │               │                    │                 │
    │ Offset Commit │                    │                 │
    │◀──────────────│                    │                 │
    │               │                    │                 │
```

### 3.3 메시지 처리 실패 시 재시도 흐름

```
┌───────┐  ┌──────────────────┐  ┌────────────────┐  ┌───────────────┐
│ Kafka │  │PaymentConsumer   │  │DataTransmission│  │   DLQ Topic   │
└───┬───┘  └────────┬─────────┘  └───────┬────────┘  └───────┬───────┘
    │               │                    │                   │
    │ ConsumerRecord│                    │                   │
    │──────────────▶│                    │                   │
    │               │                    │                   │
    │               │ send(orderData)    │                   │
    │               │───────────────────▶│                   │
    │               │                    │                   │
    │               │◀── 실패 (Exception)│                   │
    │               │                    │                   │
    │               │ [재시도 1회차]     │                   │
    │               │───────────────────▶│                   │
    │               │◀── 실패           │                   │
    │               │                    │                   │
    │               │ [재시도 2회차]     │                   │
    │               │───────────────────▶│                   │
    │               │◀── 실패           │                   │
    │               │                    │                   │
    │               │ [재시도 3회차]     │                   │
    │               │───────────────────▶│                   │
    │               │◀── 실패           │                   │
    │               │                    │                   │
    │               │ DefaultErrorHandler                   │
    │               │ (3회 재시도 후 최종 실패)              │
    │               │                    │                   │
    │               │ DLQ로 메시지 전송  │                   │
    │               │──────────────────────────────────────▶│
    │               │                    │                   │
    │ Offset Commit │                    │                   │
    │◀──────────────│                    │                   │
    │               │                    │                   │
```

---

## 4. Kafka 토픽 설계

### 4.1 토픽 구성

| 토픽명 | 용도 | 파티션 | 복제 팩터 |
|--------|------|--------|----------|
| `payment-completed` | 결제 완료 이벤트 | 3 | 1 (운영: 3) |
| `payment-completed.DLT` | 처리 실패 메시지 (Dead Letter) | 1 | 1 (운영: 3) |

### 4.2 메시지 스키마

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": "1",
  "userId": "1",
  "orderNumber": "ORD-20241218-ABC123",
  "totalAmount": 50000,
  "discountAmount": 0,
  "finalAmount": 50000,
  "paymentMethod": "BALANCE",
  "productOrderCounts": {
    "1": 2,
    "3": 1
  },
  "paidAt": "2024-12-18T10:30:00",
  "publishedAt": "2024-12-18T10:30:01"
}
```

### 4.3 파티션 전략

```
파티션 키: orderId

┌─────────────────────────────────────────────────────────────────┐
│                    Partition Assignment                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   orderId: "1" ──hash──▶ Partition 0                           │
│   orderId: "2" ──hash──▶ Partition 1                           │
│   orderId: "3" ──hash──▶ Partition 2                           │
│   orderId: "4" ──hash──▶ Partition 0                           │
│   ...                                                           │
│                                                                 │
│   장점:                                                         │
│   • 동일 주문의 이벤트는 같은 파티션에 저장                       │
│   • 파티션 내 순서 보장                                          │
│   • Consumer 간 부하 분산                                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.4 Consumer Group 설계

```
┌─────────────────────────────────────────────────────────────────┐
│              Consumer Group: data-platform-group                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│   │ Partition 0 │    │ Partition 1 │    │ Partition 2 │        │
│   └──────┬──────┘    └──────┬──────┘    └──────┬──────┘        │
│          │                  │                  │                │
│          ▼                  ▼                  ▼                │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│   │ Consumer 1  │    │ Consumer 2  │    │ Consumer 3  │        │
│   │ (Thread 1)  │    │ (Thread 2)  │    │ (Thread 3)  │        │
│   └─────────────┘    └─────────────┘    └─────────────┘        │
│                                                                 │
│   설정:                                                         │
│   • concurrency: 3 (파티션 수와 동일)                           │
│   • ack-mode: MANUAL_IMMEDIATE                                  │
│   • auto-offset-reset: earliest                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. 구현 상세

### 5.1 Producer 구현

```java
// PaymentEventProducer.java
@Component
public class PaymentEventProducer {

    public static final String TOPIC_PAYMENT_COMPLETED = "payment-completed";

    private final KafkaTemplate<String, PaymentCompletedMessage> kafkaTemplate;

    public void sendPaymentCompletedEvent(PaymentCompletedMessage message) {
        // orderId를 파티션 키로 사용
        kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, message.getOrderId(), message)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka 메시지 발행 실패: {}", ex.getMessage());
                } else {
                    log.info("Kafka 메시지 발행 성공: partition={}, offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }
}
```

### 5.2 Consumer 구현

```java
// PaymentEventConsumer.java
@Component
public class PaymentEventConsumer {

    @KafkaListener(
        topics = "payment-completed",
        groupId = "data-platform-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentCompleted(
            ConsumerRecord<String, PaymentCompletedMessage> record,
            Acknowledgment ack) {

        PaymentCompletedMessage message = record.value();

        try {
            // 외부 데이터 플랫폼으로 전송
            dataTransmissionService.send(toOrderData(message));

            // 처리 성공 시 오프셋 커밋
            ack.acknowledge();

        } catch (Exception e) {
            // 실패 시 예외 발생 -> ErrorHandler에서 재시도
            throw new RuntimeException("메시지 처리 실패", e);
        }
    }
}
```

### 5.3 핵심 설정

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

    producer:
      acks: all                    # 모든 ISR 복제 후 응답
      retries: 3                   # 실패 시 3회 재시도
      properties:
        enable.idempotence: true   # 멱등성 보장

    consumer:
      group-id: data-platform-group
      auto-offset-reset: earliest  # 처음부터 읽기
      enable-auto-commit: false    # 수동 오프셋 커밋

    listener:
      ack-mode: manual_immediate   # 즉시 수동 커밋
      concurrency: 3               # 동시 처리 스레드 수
```

---

## 6. 장애 대응 및 복구 전략

### 6.1 Producer 장애 대응

```
┌─────────────────────────────────────────────────────────────────┐
│                    Producer 장애 시나리오                        │
└─────────────────────────────────────────────────────────────────┘

시나리오 1: Kafka 연결 실패
┌─────────────┐
│ 결제 완료   │──▶ Kafka 발행 시도 ──▶ 연결 실패 ──▶ 로그 기록
│ 트랜잭션    │                                     (결제는 완료됨)
└─────────────┘

대응:
• 결제 트랜잭션은 이미 커밋된 상태이므로 영향 없음
• Kafka 발행 실패 로그 기록
• 필요 시 Outbox 패턴으로 재발행 가능

시나리오 2: 네트워크 지연
┌─────────────┐
│ 메시지 발행 │──▶ 타임아웃 ──▶ 재시도 (최대 3회) ──▶ 성공/실패
└─────────────┘

대응:
• Producer의 retries=3 설정으로 자동 재시도
• enable.idempotence=true로 중복 발행 방지
```

### 6.2 Consumer 장애 대응

```
┌─────────────────────────────────────────────────────────────────┐
│                    Consumer 장애 시나리오                        │
└─────────────────────────────────────────────────────────────────┘

시나리오 1: Consumer 크래시
┌─────────────┐
│ 메시지 처리 │──▶ Consumer 크래시 ──▶ Rebalancing ──▶ 다른 Consumer가 처리
│ 중          │                       (오프셋 커밋 전)
└─────────────┘

대응:
• 오프셋이 커밋되지 않았으므로 다른 Consumer가 재처리
• Consumer Group의 Rebalancing으로 자동 복구

시나리오 2: 처리 실패 (3회 재시도 후)
┌─────────────┐
│ 메시지 처리 │──▶ 3회 재시도 실패 ──▶ DLQ 전송 ──▶ 수동 처리
└─────────────┘

대응:
• DefaultErrorHandler가 3회 재시도
• 최종 실패 시 DLQ(Dead Letter Queue)로 전송
• 운영자가 DLQ 모니터링 후 수동 처리
```

### 6.3 메시지 처리 멱등성

```java
// 멱등성 보장을 위한 중복 체크
@KafkaListener(topics = "payment-completed")
public void consume(PaymentCompletedMessage message, Acknowledgment ack) {
    String eventId = message.getEventId();

    // 이미 처리된 이벤트인지 확인
    if (processedEventRepository.existsByEventId(eventId)) {
        log.info("중복 이벤트 무시: eventId={}", eventId);
        ack.acknowledge();
        return;
    }

    // 비즈니스 로직 처리
    processEvent(message);

    // 처리 완료 기록
    processedEventRepository.save(new ProcessedEvent(eventId));

    ack.acknowledge();
}
```

---

## 7. 모니터링 및 운영

### 7.1 모니터링 대시보드 (Kafka UI)

```
접속 URL: http://localhost:8090

모니터링 항목:
• 토픽 목록 및 파티션 상태
• Consumer Group의 Lag 모니터링
• 메시지 처리량 (TPS)
• Broker 상태
```

### 7.2 주요 모니터링 지표

| 지표 | 설명 | 임계값 |
|------|------|--------|
| Consumer Lag | 처리 대기 메시지 수 | > 1000 경고 |
| Message Rate | 초당 메시지 처리량 | 비정상 급증/급감 시 경고 |
| Error Rate | 처리 실패율 | > 1% 경고 |
| DLQ Message Count | DLQ 메시지 수 | > 0 즉시 확인 |

### 7.3 운영 명령어

```bash
# 토픽 목록 확인
kafka-topics --list --bootstrap-server localhost:9092

# 토픽 상세 정보
kafka-topics --describe --topic payment-completed --bootstrap-server localhost:9092

# Consumer Group Lag 확인
kafka-consumer-groups --describe --group data-platform-group --bootstrap-server localhost:9092

# 메시지 확인 (최근 10개)
kafka-console-consumer --topic payment-completed --bootstrap-server localhost:9092 --from-beginning --max-messages 10

# DLQ 메시지 확인
kafka-console-consumer --topic payment-completed.DLT --bootstrap-server localhost:9092 --from-beginning
```

### 7.4 운영 체크리스트

- [ ] Kafka Broker 상태 확인
- [ ] Consumer Group Lag 모니터링
- [ ] DLQ 메시지 확인 및 처리
- [ ] 디스크 사용량 확인
- [ ] 로그 보존 기간 관리

---

## 8. 결론

### 8.1 개선 효과 요약

| 항목 | 기존 | 개선 후 |
|------|------|---------|
| **결합도** | 강한 결합 (직접 API 호출) | 느슨한 결합 (이벤트 기반) |
| **장애 영향** | 데이터 플랫폼 장애 → 결제 지연 | 결제와 독립적 |
| **확장성** | 새 Consumer 추가 시 코드 수정 | Consumer Group 추가만으로 확장 |
| **재처리** | Outbox 패턴 (복잡) | Kafka 메시지 재생 (단순) |
| **처리량** | 동기 호출 병목 | 비동기 병렬 처리 |

### 8.2 향후 확장 가능성

1. **새로운 Consumer 추가**: 분석 시스템, 알림 서비스 등
2. **이벤트 스트림 처리**: Kafka Streams를 활용한 실시간 분석
3. **멀티 리전 복제**: Kafka MirrorMaker2로 재해 복구 구성
4. **스키마 관리**: Confluent Schema Registry로 스키마 버전 관리
