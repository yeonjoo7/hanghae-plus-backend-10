# 카프카를 활용한 쿠폰 발급 대기열 설계

## 목차
1. [현재 시스템 분석](#1-현재-시스템-분석)
2. [Kafka 적용의 필요성](#2-kafka-적용의-필요성)
3. [Kafka 기반 설계](#3-kafka-기반-설계)
4. [시퀀스 다이어그램](#4-시퀀스-다이어그램)
5. [Kafka 토픽 설계](#5-kafka-토픽-설계)
6. [구현 상세](#6-구현-상세)
7. [Redis vs Kafka 비교](#7-redis-vs-kafka-비교)
8. [장애 대응 및 운영](#8-장애-대응-및-운영)

---

## 1. 현재 시스템 분석

### 1.1 현재 아키텍처 (Redis 기반)

현재 선착순 쿠폰 발급은 Redis List를 사용한 대기열 방식으로 구현되어 있습니다.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     현재 아키텍처 (Redis 대기열)                            │
└────────────────────────────────────────────────────────────────────────────┘

┌──────────┐    ┌──────────────┐    ┌───────────────────┐    ┌──────────┐
│  Client  │───▶│CouponService │───▶│CouponQueueService │───▶│  Redis   │
│          │    │              │    │   (Redis List)    │    │          │
└──────────┘    └──────────────┘    └───────────────────┘    └──────────┘
                                             │
                                             │ 스케줄러가 주기적으로 처리
                                             ▼
                                    ┌───────────────────┐
                                    │CouponIssuance     │
                                    │   Scheduler       │
                                    │ (5초마다 100건)    │
                                    └─────────┬─────────┘
                                              │
                                              ▼
                                    ┌───────────────────┐
                                    │      MySQL        │
                                    │  (쿠폰 발급 저장)  │
                                    └───────────────────┘
```

### 1.2 현재 Redis 자료구조

```
Redis 키 구조:
├── coupon:queue:{couponId}    → List (FIFO Queue)
├── coupon:issued:{couponId}   → Set (발급 완료 사용자)
└── coupon:pending:{couponId}  → Set (대기 중인 사용자)
```

### 1.3 현재 방식의 한계점

| 한계점 | 설명 |
|--------|------|
| **데이터 영속성** | Redis는 메모리 기반으로 장애 시 데이터 손실 가능 |
| **처리량 제한** | 스케줄러가 5초마다 100건씩 처리 (초당 20건) |
| **순서 보장** | 다중 Consumer 확장 시 순서 보장 어려움 |
| **복잡한 상태 관리** | pending, issued, queue 3개 키를 동기화해야 함 |
| **재처리 어려움** | 실패한 요청의 재처리 로직이 복잡 |

---

## 2. Kafka 적용의 필요성

### 2.1 대용량 트래픽 시나리오

```
선착순 1000명 쿠폰 발급 이벤트:
├── 예상 동시 접속: 10,000+ 명
├── 초당 요청: 5,000+ TPS
├── 처리 시간: 3분 이내 완료 필요
└── 요구사항: 선착순 보장, 중복 방지, 순서 보장
```

### 2.2 Kafka 적용 시 장점

| 장점 | 설명 |
|------|------|
| **높은 처리량** | 초당 수만 건 이상의 메시지 처리 가능 |
| **데이터 영속성** | 디스크에 메시지 저장, 장애 시에도 데이터 유지 |
| **순서 보장** | 파티션 내 메시지 순서 보장 |
| **재처리 가능** | 오프셋 기반으로 실패한 메시지 재처리 가능 |
| **확장성** | Consumer Group으로 수평 확장 용이 |
| **디커플링** | Producer/Consumer 독립적 운영 가능 |

---

## 3. Kafka 기반 설계

### 3.1 개선된 아키텍처

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     개선된 아키텍처 (Kafka 기반)                            │
└────────────────────────────────────────────────────────────────────────────┘

┌──────────┐    ┌──────────────┐    ┌───────────────────┐
│  Client  │───▶│CouponService │───▶│ CouponRequest     │
│  (요청)  │    │              │    │   Producer        │
└──────────┘    └──────────────┘    └─────────┬─────────┘
                      │                       │
                      │                       ▼
                      │             ┌───────────────────────────────────────┐
                      │             │           Kafka Cluster               │
                      │             │  ┌─────────────────────────────────┐  │
                      │             │  │  Topic: coupon-issuance-request │  │
                      │             │  │  ┌─────────────────────────────┐│  │
                      │             │  │  │ P0: couponId=1 요청들        ││  │
                      │             │  │  │ P1: couponId=2 요청들        ││  │
                      │             │  │  │ P2: couponId=3 요청들        ││  │
                      │             │  │  └─────────────────────────────┘│  │
                      │             │  └─────────────────────────────────┘  │
                      │             └───────────────────────────────────────┘
                      │                             │
                      │                             ▼
                      │             ┌───────────────────────────────────────┐
                      │             │     Consumer Group: coupon-issuer     │
                      │             │  ┌───────────┐ ┌───────────┐         │
                      │             │  │Consumer 1 │ │Consumer 2 │ ...     │
                      │             │  │  (P0)     │ │  (P1)     │         │
                      │             │  └─────┬─────┘ └─────┬─────┘         │
                      │             └────────┼─────────────┼───────────────┘
                      │                      │             │
                      │                      ▼             ▼
                      │             ┌───────────────────────────────────────┐
                      │             │              MySQL                    │
                      │             │         (쿠폰 발급 저장)               │
                      │             └───────────────────────────────────────┘
                      │                             │
                      │                             ▼
                      │             ┌───────────────────────────────────────┐
                      │             │      Topic: coupon-issuance-result   │
                      │             └───────────────────────────────────────┘
                      │                             │
                      ▼                             ▼
              ┌───────────────┐           ┌───────────────────┐
              │    Redis      │◀──────────│  Result Consumer  │
              │ (발급 결과    │           │  (결과 업데이트)   │
              │  캐싱/조회)   │           └───────────────────┘
              └───────────────┘
```

### 3.2 핵심 설계 원칙

1. **쿠폰별 파티셔닝**: `couponId`를 파티션 키로 사용하여 동일 쿠폰 요청은 같은 파티션에서 순서대로 처리
2. **멱등성 보장**: eventId로 중복 처리 방지
3. **결과 토픽 분리**: 발급 결과를 별도 토픽으로 발행하여 클라이언트에게 알림
4. **Redis 캐싱**: 발급 결과 조회를 위한 캐시 유지

---

## 4. 시퀀스 다이어그램

### 4.1 쿠폰 발급 요청 흐름

```
┌──────┐  ┌──────────────┐  ┌───────────┐  ┌───────┐  ┌──────────────┐  ┌───────┐
│Client│  │CouponService │  │ Producer  │  │ Kafka │  │CouponConsumer│  │ MySQL │
└──┬───┘  └──────┬───────┘  └─────┬─────┘  └───┬───┘  └──────┬───────┘  └───┬───┘
   │             │                │            │             │              │
   │ POST /coupons/{id}/issue    │            │             │              │
   │────────────▶│                │            │             │              │
   │             │                │            │             │              │
   │             │ 유효성 검증    │            │             │              │
   │             │────────────────│            │             │              │
   │             │                │            │             │              │
   │             │ 발급 요청 메시지 발행        │             │              │
   │             │───────────────▶│            │             │              │
   │             │                │            │             │              │
   │             │                │──Produce──▶│             │              │
   │             │                │            │             │              │
   │             │                │◀── ACK ────│             │              │
   │             │                │            │             │              │
   │◀────────────│ 202 Accepted  │            │             │              │
   │             │ (대기열 등록)   │            │             │              │
   │             │                │            │             │              │
   │             │                │            │── Poll ────▶│              │
   │             │                │            │             │              │
   │             │                │            │             │ 발급 처리    │
   │             │                │            │             │─────────────▶│
   │             │                │            │             │              │
   │             │                │            │             │◀─ 발급 완료 ─│
   │             │                │            │             │              │
   │             │                │            │◀── Commit ──│              │
   │             │                │            │             │              │
```

### 4.2 쿠폰 발급 결과 조회 흐름

```
┌──────┐  ┌──────────────┐  ┌───────┐
│Client│  │CouponService │  │ Redis │
└──┬───┘  └──────┬───────┘  └───┬───┘
   │             │              │
   │ GET /coupons/{id}/status  │
   │────────────▶│              │
   │             │              │
   │             │ 발급 상태 조회│
   │             │─────────────▶│
   │             │              │
   │             │◀─ 상태 반환 ─│
   │             │              │
   │◀────────────│              │
   │  발급 상태  │              │
   │  (대기중/   │              │
   │   완료/실패)│              │
```

---

## 5. Kafka 토픽 설계

### 5.1 토픽 구성

| 토픽명 | 용도 | 파티션 | 복제 팩터 | 보존 기간 |
|--------|------|--------|----------|----------|
| `coupon-issuance-request` | 쿠폰 발급 요청 | 3 | 3 | 7일 |
| `coupon-issuance-result` | 발급 결과 알림 | 3 | 3 | 1일 |
| `coupon-issuance-request.DLT` | 처리 실패 요청 | 1 | 3 | 30일 |

### 5.2 메시지 스키마

#### 발급 요청 메시지 (coupon-issuance-request)
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "couponId": 1,
  "userId": 12345,
  "requestedAt": "2024-12-18T10:30:00",
  "clientIp": "192.168.1.100"
}
```

#### 발급 결과 메시지 (coupon-issuance-result)
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "couponId": 1,
  "userId": 12345,
  "status": "SUCCESS",
  "userCouponId": 67890,
  "message": "쿠폰이 성공적으로 발급되었습니다.",
  "processedAt": "2024-12-18T10:30:05"
}
```

### 5.3 파티션 전략

```
┌─────────────────────────────────────────────────────────────────┐
│         Partition Key: couponId (해시 기반 분산)                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   파티션 수: 3개 (고정)                                          │
│   - 파티션은 줄일 수 없으므로 적정 수준으로 유지                  │
│   - 트래픽 증가 시 파티션 추가 가능                              │
│                                                                 │
│   해시 분산 방식:                                                │
│   couponId.hashCode() % 3 = 파티션 번호                         │
│                                                                 │
│   couponId: 1 ──hash──▶ Partition 1                            │
│   couponId: 2 ──hash──▶ Partition 2                            │
│   couponId: 3 ──hash──▶ Partition 0                            │
│   couponId: 4 ──hash──▶ Partition 1                            │
│   ...                                                           │
│                                                                 │
│   장점:                                                         │
│   • 동일 쿠폰의 모든 요청이 같은 파티션에서 순서대로 처리        │
│   • 쿠폰 ID가 해시되어 파티션에 골고루 분산                      │
│   • 파티션별로 독립적인 Consumer가 처리하여 병렬성 확보          │
│                                                                 │
│   주의:                                                         │
│   • 인기 쿠폰에 요청이 몰리면 특정 파티션에 부하 집중 가능       │
│   • 이 경우 해당 쿠폰을 별도 토픽으로 분리하는 것을 고려         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. 구현 상세

### 6.1 Producer 구현

```java
@Component
public class CouponIssuanceProducer {

    private static final String TOPIC = "coupon-issuance-request";

    private final KafkaTemplate<String, CouponIssuanceRequest> kafkaTemplate;

    public void requestCouponIssuance(Long couponId, Long userId) {
        CouponIssuanceRequest request = new CouponIssuanceRequest(
            UUID.randomUUID().toString(),
            couponId,
            userId,
            LocalDateTime.now()
        );

        // couponId를 파티션 키로 사용하여 동일 쿠폰은 같은 파티션으로
        String partitionKey = String.valueOf(couponId);

        kafkaTemplate.send(TOPIC, partitionKey, request)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("쿠폰 발급 요청 발행 실패: couponId={}, userId={}",
                        couponId, userId, ex);
                } else {
                    log.info("쿠폰 발급 요청 발행 성공: partition={}, offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }
}
```

### 6.2 Consumer 구현

```java
@Component
public class CouponIssuanceConsumer {

    private final CouponService couponService;
    private final CouponIssuanceResultProducer resultProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(
        topics = "coupon-issuance-request",
        groupId = "coupon-issuer-group",
        concurrency = "3"  // 파티션 수와 동일
    )
    public void processCouponIssuance(
            ConsumerRecord<String, CouponIssuanceRequest> record,
            Acknowledgment ack) {

        CouponIssuanceRequest request = record.value();

        try {
            // 1. 중복 처리 체크 (멱등성)
            if (isAlreadyProcessed(request.getEventId())) {
                log.info("중복 요청 무시: eventId={}", request.getEventId());
                ack.acknowledge();
                return;
            }

            // 2. 실제 쿠폰 발급 처리
            UserCoupon userCoupon = couponService.issueCouponFromQueue(
                request.getCouponId(),
                request.getUserId()
            );

            // 3. 처리 완료 마킹 (멱등성 보장)
            markAsProcessed(request.getEventId());

            // 4. 성공 결과 발행
            resultProducer.sendSuccessResult(request, userCoupon);

            // 5. Redis 상태 업데이트
            updateRedisStatus(request, "SUCCESS", userCoupon.getId());

            // 6. 오프셋 커밋
            ack.acknowledge();

        } catch (CouponAlreadyIssuedException e) {
            // 이미 발급된 경우 - 정상 처리로 간주
            markAsProcessed(request.getEventId());
            resultProducer.sendFailResult(request, "ALREADY_ISSUED", e.getMessage());
            updateRedisStatus(request, "ALREADY_ISSUED", null);
            ack.acknowledge();

        } catch (IllegalStateException e) {
            // 쿠폰 소진 등 - 정상 처리로 간주
            markAsProcessed(request.getEventId());
            resultProducer.sendFailResult(request, "EXHAUSTED", e.getMessage());
            updateRedisStatus(request, "EXHAUSTED", null);
            ack.acknowledge();

        } catch (Exception e) {
            // 예기치 않은 에러 - 재시도 또는 DLQ
            log.error("쿠폰 발급 처리 실패: {}", request, e);
            throw e;  // ErrorHandler가 재시도 처리
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        String key = "coupon:processed:" + eventId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markAsProcessed(String eventId) {
        String key = "coupon:processed:" + eventId;
        redisTemplate.opsForValue().set(key, "1", Duration.ofDays(1));
    }

    private void updateRedisStatus(CouponIssuanceRequest request,
                                   String status, Long userCouponId) {
        String key = "coupon:status:" + request.getCouponId() + ":" + request.getUserId();
        Map<String, Object> statusMap = Map.of(
            "status", status,
            "userCouponId", userCouponId != null ? userCouponId : "",
            "processedAt", LocalDateTime.now().toString()
        );
        redisTemplate.opsForHash().putAll(key, statusMap);
        redisTemplate.expire(key, Duration.ofDays(7));
    }
}
```

### 6.3 Kafka 설정

```java
@Configuration
public class CouponKafkaConfig {

    @Bean
    public NewTopic couponIssuanceRequestTopic() {
        return TopicBuilder.name("coupon-issuance-request")
                .partitions(3)   // 파티션은 줄일 수 없으므로 적정 수준 유지
                .replicas(1)     // 운영: 3
                .build();
    }

    @Bean
    public NewTopic couponIssuanceResultTopic() {
        return TopicBuilder.name("coupon-issuance-result")
                .partitions(3)
                .replicas(1)     // 운영: 3
                .build();
    }

    @Bean
    public NewTopic couponIssuanceDltTopic() {
        return TopicBuilder.name("coupon-issuance-request.DLT")
                .partitions(1)
                .replicas(1)     // 운영: 3
                .build();
    }
}
```

---

## 7. Redis vs Kafka 비교

### 7.1 기능 비교

| 항목 | Redis (현재) | Kafka (개선) |
|------|-------------|--------------|
| **처리량** | 초당 ~1,000건 | 초당 10,000건+ |
| **순서 보장** | List로 FIFO 보장 | 파티션 내 순서 보장 |
| **데이터 영속성** | RDB/AOF 필요 | 기본 디스크 저장 |
| **재처리** | 복잡한 구현 필요 | 오프셋으로 간단 재처리 |
| **확장성** | 단일 인스턴스 | Consumer Group 확장 |
| **상태 관리** | 3개 키 동기화 | 메시지 기반 단순화 |
| **모니터링** | 별도 구현 필요 | Kafka UI로 통합 |

### 7.2 적용 시나리오별 권장

```
┌─────────────────────────────────────────────────────────────────┐
│                     시나리오별 권장 솔루션                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   [Redis 권장]                                                  │
│   • 소규모 이벤트 (동시 접속 1,000명 이하)                       │
│   • 실시간 조회가 중요한 경우                                    │
│   • 인프라 단순화가 필요한 경우                                  │
│                                                                 │
│   [Kafka 권장]                                                  │
│   • 대규모 이벤트 (동시 접속 10,000명 이상)                      │
│   • 데이터 영속성이 중요한 경우                                  │
│   • 이벤트 재처리가 필요한 경우                                  │
│   • 다른 시스템과의 연동이 필요한 경우                           │
│                                                                 │
│   [하이브리드 권장]                                              │
│   • Kafka: 요청 버퍼링 및 처리                                   │
│   • Redis: 발급 결과 캐싱 및 빠른 조회                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 8. 장애 대응 및 운영

### 8.1 장애 시나리오 및 대응

```
시나리오 1: Kafka Broker 장애
┌─────────────────────────────────────────────────────────────────┐
│ 대응: Replication Factor 3으로 자동 Failover                    │
│ • 다른 Broker가 Leader로 승격                                   │
│ • 클라이언트 자동 재연결                                         │
└─────────────────────────────────────────────────────────────────┘

시나리오 2: Consumer 장애
┌─────────────────────────────────────────────────────────────────┐
│ 대응: Consumer Group Rebalancing                                │
│ • 다른 Consumer가 해당 파티션 담당                               │
│ • 오프셋 커밋 전 메시지는 자동 재처리                             │
└─────────────────────────────────────────────────────────────────┘

시나리오 3: 쿠폰 발급 실패 (DB 에러 등)
┌─────────────────────────────────────────────────────────────────┐
│ 대응: 재시도 후 DLQ                                             │
│ • 3회 재시도 (1초 간격)                                         │
│ • 실패 시 DLQ로 이동                                            │
│ • 운영자가 DLQ 모니터링 후 수동 처리                             │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 모니터링 지표

| 지표 | 설명 | 임계값 |
|------|------|--------|
| Consumer Lag | 처리 대기 메시지 수 | > 10,000 경고 |
| Processing Rate | 초당 처리량 | < 100 경고 |
| Error Rate | 처리 실패율 | > 1% 경고 |
| DLQ Message Count | DLQ 메시지 수 | > 0 즉시 확인 |

### 8.3 운영 명령어

```bash
# 토픽 상태 확인
kafka-topics --describe --topic coupon-issuance-request --bootstrap-server localhost:9092

# Consumer Lag 확인
kafka-consumer-groups --describe --group coupon-issuer-group --bootstrap-server localhost:9092

# DLQ 메시지 확인
kafka-console-consumer --topic coupon-issuance-request.DLT --bootstrap-server localhost:9092 --from-beginning

# 특정 쿠폰의 요청 확인
kafka-console-consumer --topic coupon-issuance-request --bootstrap-server localhost:9092 --property print.key=true --property key.separator=": "
```

---

## 9. 결론

### 9.1 도입 효과 요약

| 항목 | 개선 전 | 개선 후 |
|------|---------|---------|
| **처리량** | 초당 20건 (스케줄러) | 초당 10,000건+ |
| **확장성** | 단일 스케줄러 | Consumer 수평 확장 |
| **데이터 안정성** | Redis 메모리 의존 | Kafka 디스크 영속화 |
| **재처리** | 수동 구현 필요 | 오프셋 기반 자동화 |
| **모니터링** | 별도 구현 | Kafka UI 통합 |

### 9.2 구현 우선순위

1. **Phase 1**: Kafka Producer 구현 (요청 버퍼링)
2. **Phase 2**: Kafka Consumer 구현 (발급 처리)
3. **Phase 3**: 결과 토픽 및 Redis 캐싱 연동
4. **Phase 4**: 모니터링 및 DLQ 운영 체계 구축

### 9.3 참고사항

- 현재 Redis 기반 대기열도 유효한 솔루션이므로, 트래픽 규모에 따라 선택적 적용
- Kafka 도입 시 인프라 복잡도 증가를 고려하여 점진적 마이그레이션 권장
- 기존 Redis 로직은 발급 결과 캐싱 용도로 유지
