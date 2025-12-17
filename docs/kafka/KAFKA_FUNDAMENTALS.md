# Apache Kafka 핵심 개념 학습 문서

## 목차
1. [Kafka란 무엇인가?](#1-kafka란-무엇인가)
2. [핵심 구성 요소](#2-핵심-구성-요소)
3. [메시지 흐름과 아키텍처](#3-메시지-흐름과-아키텍처)
4. [Producer 상세](#4-producer-상세)
5. [Consumer 상세](#5-consumer-상세)
6. [신뢰성과 내구성](#6-신뢰성과-내구성)
7. [성능 최적화](#7-성능-최적화)
8. [로컬 환경 설정 및 실습](#8-로컬-환경-설정-및-실습)
9. [Spring Boot 연동](#9-spring-boot-연동)

---

## 1. Kafka란 무엇인가?

### 1.1 정의
Apache Kafka는 **분산 이벤트 스트리밍 플랫폼**으로, LinkedIn에서 개발되어 2011년 Apache 재단에 오픈소스로 기증되었습니다.

### 1.2 주요 특징
| 특징 | 설명 |
|------|------|
| **고처리량(High Throughput)** | 초당 수백만 건의 메시지 처리 가능 |
| **확장성(Scalability)** | 수평적 확장이 용이한 분산 시스템 |
| **내구성(Durability)** | 디스크에 메시지를 영구 저장 |
| **내결함성(Fault Tolerance)** | 복제를 통한 장애 대응 |
| **실시간 처리** | 밀리초 단위의 낮은 지연 시간 |

### 1.3 기존 메시징 시스템과의 차이점

```
┌─────────────────────────────────────────────────────────────────────┐
│                     전통적인 메시지 큐 (RabbitMQ 등)                    │
├─────────────────────────────────────────────────────────────────────┤
│  • 메시지 소비 후 삭제                                                 │
│  • Point-to-Point 또는 Pub/Sub 중 선택                               │
│  • 복잡한 라우팅 지원                                                  │
│  • 상대적으로 낮은 처리량                                              │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                          Apache Kafka                               │
├─────────────────────────────────────────────────────────────────────┤
│  • 메시지를 보존 기간 동안 유지 (재처리 가능)                            │
│  • Pub/Sub + 메시지 로그 결합                                         │
│  • 파티션 기반 병렬 처리                                               │
│  • 매우 높은 처리량                                                   │
│  • Consumer Group을 통한 유연한 소비 모델                              │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.4 주요 사용 사례
- **실시간 데이터 파이프라인**: 시스템 간 데이터 동기화
- **이벤트 소싱**: 상태 변경을 이벤트로 기록
- **로그 수집**: 분산 시스템의 로그 중앙 집중화
- **스트림 처리**: 실시간 데이터 분석 및 변환
- **마이크로서비스 통신**: 서비스 간 비동기 메시징

---

## 2. 핵심 구성 요소

### 2.1 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Kafka Cluster                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                         │
│  │  Broker 1   │  │  Broker 2   │  │  Broker 3   │                         │
│  │  (Leader)   │  │  (Follower) │  │  (Follower) │                         │
│  │             │  │             │  │             │                         │
│  │ ┌─────────┐ │  │ ┌─────────┐ │  │ ┌─────────┐ │                         │
│  │ │Topic A  │ │  │ │Topic A  │ │  │ │Topic A  │ │                         │
│  │ │Part 0   │ │  │ │Part 1   │ │  │ │Part 2   │ │                         │
│  │ └─────────┘ │  │ └─────────┘ │  │ └─────────┘ │                         │
│  └─────────────┘  └─────────────┘  └─────────────┘                         │
│         ▲                                  │                                │
│         │              Replication         │                                │
│         └──────────────────────────────────┘                                │
└─────────────────────────────────────────────────────────────────────────────┘
         ▲                                                      │
         │                                                      ▼
┌─────────────────┐                                  ┌─────────────────┐
│    Producers    │                                  │    Consumers    │
│  (메시지 발행)   │                                  │  (메시지 소비)   │
└─────────────────┘                                  └─────────────────┘
```

### 2.2 Broker (브로커)
- Kafka 서버 인스턴스
- 메시지 저장 및 전달 담당
- 클러스터 내 여러 브로커가 협력

```java
// Broker 설정 예시 (server.properties)
broker.id=0                          // 브로커 고유 ID
listeners=PLAINTEXT://localhost:9092 // 리스너 설정
log.dirs=/var/kafka-logs             // 로그 저장 경로
num.partitions=3                     // 기본 파티션 수
```

### 2.3 Topic (토픽)
- 메시지를 분류하는 논리적 채널
- 데이터베이스의 테이블과 유사한 개념
- 여러 파티션으로 구성

```
┌───────────────────── Topic: "payment-events" ─────────────────────┐
│                                                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │ Partition 0  │  │ Partition 1  │  │ Partition 2  │             │
│  │              │  │              │  │              │             │
│  │ [0][1][2][3] │  │ [0][1][2]    │  │ [0][1][2][3] │             │
│  │  └─offset    │  │              │  │         [4]  │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

### 2.4 Partition (파티션)
- 토픽의 물리적 분할 단위
- 순서가 보장되는 불변의 메시지 시퀀스
- 병렬 처리의 기본 단위

**파티션의 장점:**
1. **병렬 처리**: 여러 Consumer가 동시에 읽기 가능
2. **확장성**: 파티션 추가로 처리량 증가
3. **순서 보장**: 파티션 내에서 메시지 순서 보장

### 2.5 Offset (오프셋)
- 파티션 내 메시지의 고유 순번
- 0부터 시작하여 단조 증가
- Consumer가 읽은 위치를 추적

```
Partition 0:
┌─────┬─────┬─────┬─────┬─────┬─────┐
│  0  │  1  │  2  │  3  │  4  │  5  │  ← Offset
├─────┼─────┼─────┼─────┼─────┼─────┤
│ Msg │ Msg │ Msg │ Msg │ Msg │ Msg │  ← Messages
└─────┴─────┴─────┴─────┴─────┴─────┘
                    ▲
                    │
            Current Consumer
               Position
```

### 2.6 Producer (프로듀서)
- 메시지를 토픽에 발행하는 클라이언트
- 파티션 선택 전략 결정
- 메시지 직렬화 담당

### 2.7 Consumer (컨슈머)
- 토픽에서 메시지를 읽는 클라이언트
- Consumer Group에 소속
- 오프셋 관리

### 2.8 Consumer Group (컨슈머 그룹)
- 논리적인 Consumer 집합
- 파티션을 그룹 내 Consumer에게 분배
- 동일 그룹 내에서 메시지 중복 소비 방지

```
┌─────────────────────────────────────────────────────────────────┐
│                    Topic: payment-events                        │
│    ┌───────────┐  ┌───────────┐  ┌───────────┐                 │
│    │Partition 0│  │Partition 1│  │Partition 2│                 │
│    └─────┬─────┘  └─────┬─────┘  └─────┬─────┘                 │
│          │              │              │                        │
└──────────┼──────────────┼──────────────┼────────────────────────┘
           │              │              │
           ▼              ▼              ▼
┌──────────────────────────────────────────────────────────────────┐
│                   Consumer Group: "data-platform"                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │ Consumer 1  │  │ Consumer 2  │  │ Consumer 3  │              │
│  │ (P0 담당)   │  │ (P1 담당)   │  │ (P2 담당)   │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                   Consumer Group: "analytics"                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              Consumer A (모든 파티션 담당)                │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

### 2.9 KRaft (Kafka Raft)

Kafka 3.3부터 **KRaft 모드**가 정식 지원되어 Zookeeper 없이 Kafka를 운영할 수 있습니다.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Zookeeper 모드 vs KRaft 모드                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   [기존: Zookeeper 모드]              [신규: KRaft 모드]                    │
│                                                                             │
│   ┌─────────────┐                     ┌─────────────┐                      │
│   │  Zookeeper  │                     │   Broker    │                      │
│   │  Ensemble   │                     │ +Controller │                      │
│   └──────┬──────┘                     └─────────────┘                      │
│          │                                   │                              │
│          ▼                            Raft 합의 프로토콜                    │
│   ┌─────────────┐                            │                              │
│   │   Broker    │                     ┌──────▼──────┐                      │
│   │   Cluster   │                     │  Metadata   │                      │
│   └─────────────┘                     │    Log      │                      │
│                                       └─────────────┘                      │
│                                                                             │
│   • 외부 의존성 존재                  • 외부 의존성 없음                     │
│   • 복잡한 운영                       • 단순한 아키텍처                      │
│   • 메타데이터 동기화 지연            • 빠른 메타데이터 동기화               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**KRaft의 장점:**
- **운영 단순화**: Zookeeper 클러스터 관리 불필요
- **빠른 시작**: 단일 프로세스로 시작 가능
- **메타데이터 성능**: Raft 기반으로 빠른 동기화
- **확장성**: 수백만 파티션까지 확장 가능

**KRaft 핵심 설정:**
```properties
# KRaft 모드 필수 설정
process.roles=broker,controller          # 브로커와 컨트롤러 역할 겸임
node.id=1                                 # 노드 고유 ID
controller.quorum.voters=1@localhost:9093 # 컨트롤러 투표 구성원
controller.listener.names=CONTROLLER      # 컨트롤러 리스너 이름
```

---

## 3. 메시지 흐름과 아키텍처

### 3.1 메시지 발행 흐름

```
┌───────────────────────────────────────────────────────────────────────────┐
│                           Producer Application                            │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  1. 메시지 생성                                                       │  │
│  │     ProducerRecord(topic, key, value)                                │  │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                    │                                       │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  2. 직렬화 (Serializer)                                              │  │
│  │     Key/Value → byte[]                                               │  │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                    │                                       │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  3. 파티션 결정 (Partitioner)                                        │  │
│  │     • Key 있음: hash(key) % partition_count                          │  │
│  │     • Key 없음: Round-Robin 또는 Sticky Partitioner                  │  │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                    │                                       │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  4. 배치 처리 (RecordAccumulator)                                    │  │
│  │     메시지를 배치로 모아서 전송 효율 향상                              │  │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                    │                                       │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  5. 네트워크 전송 (Sender Thread)                                    │  │
│  │     배치를 브로커로 전송                                              │  │
│  └───────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                           ┌───────────────┐
                           │ Kafka Broker  │
                           └───────────────┘
```

### 3.2 메시지 소비 흐름

```
┌───────────────────────────────────────────────────────────────────────────┐
│                          Consumer Application                             │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  1. 파티션 할당 (Consumer Group Coordinator)                         │ │
│  │     Rebalancing을 통해 파티션 분배                                    │ │
│  └───────────────────────────────────────────────────────────────────────┘│
│                                    │                                      │
│                                    ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  2. 메시지 Fetch                                                     │ │
│  │     poll() 호출로 브로커에서 메시지 가져오기                          │ │
│  └───────────────────────────────────────────────────────────────────────┘│
│                                    │                                      │
│                                    ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  3. 역직렬화 (Deserializer)                                          │ │
│  │     byte[] → Key/Value 객체                                          │ │
│  └───────────────────────────────────────────────────────────────────────┘│
│                                    │                                      │
│                                    ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  4. 메시지 처리                                                      │ │
│  │     비즈니스 로직 실행                                                │ │
│  └───────────────────────────────────────────────────────────────────────┘│
│                                    │                                      │
│                                    ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  5. 오프셋 커밋                                                      │ │
│  │     처리 완료된 오프셋을 __consumer_offsets 토픽에 저장               │ │
│  └───────────────────────────────────────────────────────────────────────┘│
└───────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Replication (복제)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Topic: payment-events                            │
│                        Partition: 0                                     │
│                        Replication Factor: 3                            │
│                                                                         │
│  ┌───────────────┐     ┌───────────────┐     ┌───────────────┐         │
│  │   Broker 1    │     │   Broker 2    │     │   Broker 3    │         │
│  │   (LEADER)    │────▶│  (FOLLOWER)   │     │  (FOLLOWER)   │         │
│  │               │     │               │◀────│               │         │
│  │ [0][1][2][3]  │     │ [0][1][2][3]  │     │ [0][1][2][3]  │         │
│  │               │     │               │     │               │         │
│  └───────────────┘     └───────────────┘     └───────────────┘         │
│         ▲                                                               │
│         │                                                               │
│    Write/Read                                                           │
│         │                                                               │
│  ┌──────┴──────┐                                                        │
│  │  Producer/  │                                                        │
│  │  Consumer   │                                                        │
│  └─────────────┘                                                        │
└─────────────────────────────────────────────────────────────────────────┘
```

**ISR (In-Sync Replicas)**:
- Leader와 동기화된 Replica 집합
- ISR에 속한 Follower만 Leader 승격 가능
- `min.insync.replicas` 설정으로 최소 동기화 수 지정

---

## 4. Producer 상세

### 4.1 주요 설정

| 설정 | 설명 | 권장값 |
|------|------|--------|
| `bootstrap.servers` | Kafka 브로커 주소 | 3개 이상 지정 |
| `key.serializer` | Key 직렬화 클래스 | StringSerializer |
| `value.serializer` | Value 직렬화 클래스 | JsonSerializer |
| `acks` | 응답 대기 설정 | `all` (최고 신뢰성) |
| `retries` | 재시도 횟수 | 3 이상 |
| `batch.size` | 배치 크기 (bytes) | 16384 |
| `linger.ms` | 배치 대기 시간 | 5-10ms |
| `compression.type` | 압축 방식 | lz4, snappy |

### 4.2 Acks 설정

```
acks=0 (Fire-and-Forget)
┌──────────┐          ┌──────────┐
│ Producer │─────────▶│  Broker  │
│          │ No Wait  │          │
└──────────┘          └──────────┘
• 가장 빠름, 데이터 손실 가능

acks=1 (Leader Only)
┌──────────┐          ┌──────────┐
│ Producer │─────────▶│  Leader  │──ACK──▶ Producer
│          │          │          │
└──────────┘          └──────────┘
• Leader 저장 후 응답, Follower 복제 전 손실 가능

acks=all (-1) (All Replicas)
┌──────────┐          ┌──────────┐
│ Producer │─────────▶│  Leader  │────┐
│          │          │          │    │ Replicate
└──────────┘          └──────────┘    ▼
     ▲                            ┌──────────┐
     │                            │ Follower │
     └────────── ACK ─────────────└──────────┘
• 모든 ISR 복제 후 응답, 가장 안전
```

### 4.3 Idempotent Producer (멱등성 프로듀서)

```java
// 설정
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
props.put(ProducerConfig.ACKS_CONFIG, "all");
props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
```

**동작 원리**:
- Producer ID (PID) + Sequence Number로 중복 감지
- 네트워크 재시도로 인한 중복 메시지 방지

### 4.4 Transactional Producer (트랜잭션 프로듀서)

```java
producer.initTransactions();
try {
    producer.beginTransaction();
    producer.send(record1);
    producer.send(record2);
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

**사용 사례**:
- 여러 토픽/파티션에 원자적 쓰기
- Exactly-Once Semantics (EOS) 구현

---

## 5. Consumer 상세

### 5.1 주요 설정

| 설정 | 설명 | 권장값 |
|------|------|--------|
| `bootstrap.servers` | Kafka 브로커 주소 | 3개 이상 지정 |
| `group.id` | Consumer Group ID | 서비스별 고유값 |
| `key.deserializer` | Key 역직렬화 클래스 | StringDeserializer |
| `value.deserializer` | Value 역직렬화 클래스 | JsonDeserializer |
| `auto.offset.reset` | 오프셋 없을 때 시작점 | `earliest` / `latest` |
| `enable.auto.commit` | 자동 오프셋 커밋 | `false` (권장) |
| `max.poll.records` | poll당 최대 레코드 | 500 |
| `max.poll.interval.ms` | poll 간격 제한 | 300000 |

### 5.2 오프셋 커밋 전략

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Auto Commit (자동 커밋)                           │
├─────────────────────────────────────────────────────────────────────────┤
│  enable.auto.commit=true                                                │
│  auto.commit.interval.ms=5000                                           │
│                                                                         │
│  장점: 구현 간단                                                         │
│  단점: 처리 완료 전 커밋되어 메시지 손실 가능                              │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                        Manual Commit (수동 커밋)                         │
├─────────────────────────────────────────────────────────────────────────┤
│  enable.auto.commit=false                                               │
│                                                                         │
│  // 동기 커밋                                                           │
│  consumer.commitSync();                                                 │
│                                                                         │
│  // 비동기 커밋                                                         │
│  consumer.commitAsync((offsets, exception) -> {                         │
│      if (exception != null) log.error("Commit failed", exception);      │
│  });                                                                    │
│                                                                         │
│  장점: 정확한 오프셋 관리                                                │
│  단점: 구현 복잡도 증가                                                  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.3 Consumer Rebalancing

```
Consumer Group 변화 시 Rebalancing 발생:
• Consumer 추가/제거
• Consumer 장애
• 토픽 파티션 변경

Rebalancing 과정:
1. Group Coordinator가 Rebalance 시작
2. 모든 Consumer가 파티션 해제 (Stop-the-World)
3. 파티션 재할당
4. Consumer가 새 파티션에서 처리 시작

┌─────────────────────────────────────────────────────────────────┐
│  Before Rebalancing              After Rebalancing              │
│  ┌───────┐ ┌───────┐            ┌───────┐ ┌───────┐            │
│  │  C1   │ │  C2   │            │  C1   │ │  C2   │ ┌───────┐ │
│  │ P0,P1 │ │ P2,P3 │     ──▶    │  P0   │ │ P1,P2 │ │  C3   │ │
│  └───────┘ └───────┘            └───────┘ └───────┘ │  P3   │ │
│                                                     └───────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 5.4 Partition Assignment Strategy

| 전략 | 설명 |
|------|------|
| `RangeAssignor` | 토픽별로 파티션을 범위로 할당 |
| `RoundRobinAssignor` | 모든 파티션을 순환 할당 |
| `StickyAssignor` | 기존 할당 유지하며 최소 변경 |
| `CooperativeStickyAssignor` | 점진적 Rebalancing (권장) |

---

## 6. 신뢰성과 내구성

### 6.1 메시지 전달 보장 (Delivery Semantics)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       At-Most-Once (최대 1회)                           │
├─────────────────────────────────────────────────────────────────────────┤
│  • 메시지 손실 가능, 중복 없음                                           │
│  • Producer: acks=0, 재시도 없음                                        │
│  • Consumer: 처리 전 오프셋 커밋                                        │
│  • 사용: 로그 수집 등 손실 허용 가능한 경우                               │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                       At-Least-Once (최소 1회)                          │
├─────────────────────────────────────────────────────────────────────────┤
│  • 메시지 손실 없음, 중복 가능                                           │
│  • Producer: acks=all, 재시도 활성화                                    │
│  • Consumer: 처리 후 오프셋 커밋                                        │
│  • 사용: 대부분의 비즈니스 로직 (멱등성 처리 필요)                        │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                       Exactly-Once (정확히 1회)                         │
├─────────────────────────────────────────────────────────────────────────┤
│  • 메시지 손실 없음, 중복 없음                                           │
│  • Idempotent Producer + Transactional Producer                        │
│  • Consumer: Transactional 처리 또는 외부 트랜잭션                       │
│  • 사용: 금융 거래 등 정확성이 중요한 경우                                │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Consumer 멱등성 처리

```java
// 메시지에 고유 ID 포함
public class PaymentEvent {
    private String eventId;      // UUID
    private String orderId;
    private LocalDateTime timestamp;
    // ...
}

// Consumer에서 중복 체크
@KafkaListener(topics = "payment-events")
public void consume(PaymentEvent event) {
    // 이미 처리된 이벤트인지 확인
    if (eventRepository.existsByEventId(event.getEventId())) {
        log.info("Duplicate event ignored: {}", event.getEventId());
        return;
    }

    // 비즈니스 로직 처리
    processPayment(event);

    // 처리 완료 기록
    eventRepository.markAsProcessed(event.getEventId());
}
```

### 6.3 Dead Letter Queue (DLQ)

```
실패한 메시지 처리 흐름:

┌───────────┐     ┌──────────────┐     ┌─────────────────┐
│  Original │────▶│   Consumer   │────▶│ Process Failed  │
│   Topic   │     │              │     │   (3회 재시도)   │
└───────────┘     └──────────────┘     └────────┬────────┘
                                                │
                                                ▼
                                       ┌───────────────┐
                                       │  DLQ Topic    │
                                       │ (dead-letter) │
                                       └───────────────┘
                                                │
                                                ▼
                                       ┌───────────────┐
                                       │ 수동 처리 /   │
                                       │ 재처리 로직   │
                                       └───────────────┘
```

---

## 7. 성능 최적화

### 7.1 Producer 최적화

```properties
# 배치 처리
batch.size=32768                # 32KB
linger.ms=10                    # 10ms 대기

# 압축
compression.type=lz4            # CPU 효율적

# 버퍼
buffer.memory=67108864          # 64MB

# 병렬 처리
max.in.flight.requests.per.connection=5
```

### 7.2 Consumer 최적화

```properties
# Fetch 설정
fetch.min.bytes=1024            # 최소 1KB 모아서 fetch
fetch.max.wait.ms=500           # 최대 500ms 대기
max.partition.fetch.bytes=1048576  # 파티션당 1MB

# 처리량
max.poll.records=500            # poll당 최대 500개
```

### 7.3 Broker 최적화

```properties
# 복제
num.replica.fetchers=4          # Follower fetch 스레드

# 로그
log.segment.bytes=1073741824    # 1GB 세그먼트
log.retention.hours=168         # 7일 보존

# 네트워크
num.network.threads=8
num.io.threads=16
```

---

## 8. 로컬 환경 설정 및 실습

### 8.1 Docker Compose로 Kafka 설치 (KRaft 모드)

KRaft 모드를 사용하면 Zookeeper 없이 Kafka를 단독으로 실행할 수 있습니다.

```yaml
# docker-compose.yml
version: '3.8'
services:
  # Kafka (KRaft Mode - No Zookeeper)
  kafka:
    image: apache/kafka:3.7.0
    container_name: ecommerce-kafka
    ports:
      - "9092:9092"
      - "29092:29092"
    environment:
      # KRaft 설정
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER

      # 리스너 설정
      KAFKA_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://0.0.0.0:9092,CONTROLLER://kafka:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT

      # 클러스터 설정
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

      # KRaft 클러스터 ID (고정값 사용)
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk

  # Kafka UI (선택사항)
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: ecommerce-kafka
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
    depends_on:
      - kafka
```

**KRaft 모드의 장점:**
- Zookeeper 의존성 제거로 아키텍처 단순화
- 빠른 시작 시간
- 리소스 사용량 감소

### 8.2 기본 명령어

```bash
# 토픽 생성
kafka-topics --create \
  --topic payment-events \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# 토픽 목록 조회
kafka-topics --list \
  --bootstrap-server localhost:9092

# 토픽 상세 정보
kafka-topics --describe \
  --topic payment-events \
  --bootstrap-server localhost:9092

# 메시지 발행 (Console Producer)
kafka-console-producer \
  --topic payment-events \
  --bootstrap-server localhost:9092

# 메시지 소비 (Console Consumer)
kafka-console-consumer \
  --topic payment-events \
  --bootstrap-server localhost:9092 \
  --from-beginning

# Consumer Group 상태 확인
kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group data-platform-group \
  --describe
```

---

## 9. Spring Boot 연동

### 9.1 의존성 추가

```gradle
// build.gradle
dependencies {
    implementation 'org.springframework.kafka:spring-kafka'
}
```

### 9.2 설정 (application.yml)

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5

    consumer:
      group-id: ${spring.application.name}
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: "*"

    listener:
      ack-mode: MANUAL_IMMEDIATE
      concurrency: 3
```

### 9.3 Producer 구현

```java
@Service
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentEventMessage> kafkaTemplate;

    private static final String TOPIC = "payment-events";

    public void send(PaymentEventMessage event) {
        kafkaTemplate.send(TOPIC, event.getOrderId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send message: {}", event, ex);
                } else {
                    log.info("Message sent successfully: topic={}, partition={}, offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }
}
```

### 9.4 Consumer 구현

```java
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    @KafkaListener(
        topics = "payment-events",
        groupId = "data-platform-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload PaymentEventMessage event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            log.info("Received message: partition={}, offset={}, event={}",
                partition, offset, event);

            // 비즈니스 로직 처리
            processEvent(event);

            // 수동 오프셋 커밋
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process message: {}", event, e);
            // DLQ로 전송하거나 재시도 로직 구현
            throw e;
        }
    }
}
```

### 9.5 에러 핸들링

```java
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // DLQ로 전송하는 Recoverer
        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(
                    record.topic() + ".DLT",
                    record.partition()));

        // 3회 재시도 후 DLQ로 전송
        return new DefaultErrorHandler(recoverer,
            new FixedBackOff(1000L, 3L));
    }
}
```

---

## 참고 자료

- [Apache Kafka 공식 문서](https://kafka.apache.org/documentation/)
- [Confluent Kafka 가이드](https://docs.confluent.io/)
- [Spring for Apache Kafka](https://docs.spring.io/spring-kafka/reference/)
