# 분산 트랜잭션 처리의 한계와 대응 방안

## 1. 현재 시스템 아키텍처

### 1.1 모놀리식 구조
```
┌─────────────────────────────────────────────────────────┐
│                    E-Commerce API                        │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────────┐│
│  │  User   │ │ Product │ │  Order  │ │     Payment     ││
│  │ Service │ │ Service │ │ Service │ │     Service     ││
│  └────┬────┘ └────┬────┘ └────┬────┘ └────────┬────────┘│
│       │          │          │               │          │
│       └──────────┴──────────┴───────────────┘          │
│                         │                               │
│              ┌──────────▼──────────┐                   │
│              │    Single MySQL     │                   │
│              │     (ACID 보장)     │                   │
│              └─────────────────────┘                   │
└─────────────────────────────────────────────────────────┘
```

### 1.2 현재 결제 트랜잭션 흐름
```java
@Transactional
public Payment processPayment(orderId, userId, paymentMethod) {
    // 단일 트랜잭션 내에서 모든 작업 수행
    1. 주문 상태 확인
    2. 사용자 잔액 차감      // users 테이블
    3. 재고 차감            // stocks 테이블
    4. 쿠폰 사용 처리        // user_coupons 테이블
    5. 결제 정보 생성        // payments 테이블
    6. 거래 내역 저장        // balance_transactions 테이블
    7. 주문 상태 변경        // orders 테이블

    // 실패 시 전체 롤백 (ACID 보장)
}
```

**장점**: 단일 DB에서 ACID 트랜잭션이 완벽하게 보장됨

---

## 2. 서비스 확장 시나리오: 도메인별 분리

### 2.1 MSA 전환 후 아키텍처
```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ User Service │  │Product Svc   │  │ Order Service│  │Payment Service│
│              │  │              │  │              │  │              │
│  ┌────────┐  │  │  ┌────────┐  │  │  ┌────────┐  │  │  ┌────────┐  │
│  │User DB │  │  │  │Product │  │  │  │Order DB│  │  │  │Payment │  │
│  │(MySQL) │  │  │  │DB      │  │  │  │(MySQL) │  │  │  │DB      │  │
│  └────────┘  │  │  └────────┘  │  │  └────────┘  │  │  └────────┘  │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

### 2.2 분리된 결제 트랜잭션 흐름
```
Payment Service에서 결제 요청 시:

1. [User DB] 잔액 차감 요청     → 성공
2. [Product DB] 재고 차감 요청  → 성공
3. [Order DB] 쿠폰 사용 처리    → 실패! ❌

문제: 1, 2번은 이미 커밋됨. 어떻게 롤백할 것인가?
```

---

## 3. 분산 트랜잭션의 핵심 문제

### 3.1 2PC (Two-Phase Commit)의 한계

```
┌─────────────┐        ┌─────────────┐        ┌─────────────┐
│ Coordinator │        │ Participant │        │ Participant │
│  (Payment)  │        │   (User)    │        │  (Product)  │
└──────┬──────┘        └──────┬──────┘        └──────┬──────┘
       │                      │                      │
       │──── Prepare ────────▶│                      │
       │──── Prepare ─────────────────────────────▶ │
       │                      │                      │
       │◀─── Vote Yes ────────│                      │
       │◀─── Vote Yes ─────────────────────────────│
       │                      │                      │
       │──── Commit ─────────▶│                      │
       │──── Commit ──────────────────────────────▶ │
       │                      │                      │
       ▼                      ▼                      ▼
```

**문제점**:
- **블로킹**: Prepare 후 Commit까지 모든 참여자가 락을 유지
- **단일 장애점**: Coordinator 장애 시 전체 시스템 중단
- **성능 저하**: 네트워크 왕복 시간 × 참여자 수
- **복잡성**: 분산 환경에서 구현 및 운영 난이도 높음

### 3.2 CAP 정리의 제약

```
         Consistency (일관성)
              /\
             /  \
            /    \
           /      \
          / 분산   \
         / 시스템   \
        /____________\
Availability        Partition
(가용성)            Tolerance
                    (분할 허용)

네트워크 분할 상황에서 C와 A 중 하나만 선택 가능
```

**이커머스에서의 선택**:
- 결제: **CP** (일관성 우선) - 잔액/재고 정합성 필수
- 상품 조회: **AP** (가용성 우선) - 약간의 지연 허용

---

## 4. 대응 방안

### 4.1 SAGA 패턴

#### Choreography 방식 (이벤트 기반)
```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Payment   │     │    User     │     │   Product   │
│   Service   │     │   Service   │     │   Service   │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       │─── PaymentCreated ──▶                 │
       │                   │                   │
       │                   │── BalanceDeducted ──▶
       │                   │                   │
       │                   │      ◀── StockReduced ──
       │                   │                   │
       │◀── OrderCompleted ───────────────────│
       │                   │                   │

실패 시 보상 트랜잭션:
       │◀── StockReductionFailed ─────────────│
       │                   │                   │
       │─── RefundBalance ──▶                  │
       │                   │                   │
```

**장점**: 느슨한 결합, 높은 확장성
**단점**: 흐름 파악 어려움, 디버깅 복잡

#### Orchestration 방식 (중앙 조정자)
```
┌─────────────────────────────────────────────────────────┐
│                    Payment Saga Orchestrator             │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Step 1: Deduct Balance  → Success → Next        │    │
│  │ Step 2: Reduce Stock    → Success → Next        │    │
│  │ Step 3: Use Coupon      → Fail    → Compensate  │    │
│  │                                                  │    │
│  │ Compensation:                                    │    │
│  │   - Restore Stock                               │    │
│  │   - Refund Balance                              │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
         │              │              │
         ▼              ▼              ▼
    User Service  Product Service  Coupon Service
```

**장점**: 흐름 명확, 중앙 관리
**단점**: Orchestrator가 단일 장애점이 될 수 있음

### 4.2 현재 시스템에 SAGA 패턴 적용

```java
// PaymentSagaOrchestrator.java
@Service
public class PaymentSagaOrchestrator {

    public PaymentResult processPayment(PaymentRequest request) {
        SagaExecution saga = SagaExecution.start();

        try {
            // Step 1: 잔액 차감
            BalanceResult balanceResult = userService.deductBalance(request);
            saga.addCompensation(() -> userService.refundBalance(balanceResult));

            // Step 2: 재고 차감
            StockResult stockResult = productService.reduceStock(request);
            saga.addCompensation(() -> productService.restoreStock(stockResult));

            // Step 3: 쿠폰 사용
            if (request.hasCoupon()) {
                CouponResult couponResult = couponService.useCoupon(request);
                saga.addCompensation(() -> couponService.cancelCoupon(couponResult));
            }

            // Step 4: 결제 완료
            return paymentRepository.save(Payment.completed(request));

        } catch (Exception e) {
            // 보상 트랜잭션 실행 (역순)
            saga.compensate();
            throw new PaymentFailedException(e);
        }
    }
}
```

### 4.3 Outbox 패턴 (현재 구현됨)

```
┌─────────────────────────────────────────────────────────┐
│                    Payment Service                       │
│                                                          │
│  ┌─────────────────────────────────────────────────┐    │
│  │              Single Transaction                  │    │
│  │  1. payments INSERT                             │    │
│  │  2. outbox_events INSERT (이벤트 저장)          │    │
│  └─────────────────────────────────────────────────┘    │
│                         │                                │
│  ┌──────────────────────▼──────────────────────────┐    │
│  │           Outbox Poller (별도 프로세스)          │    │
│  │  - outbox_events 테이블 폴링                    │    │
│  │  - 메시지 브로커로 발행                          │    │
│  │  - 발행 성공 시 이벤트 삭제/완료 처리            │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
              ┌───────────────────┐
              │  Message Broker   │
              │  (Kafka/RabbitMQ) │
              └───────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    User Service   Product Service  Analytics
```

**현재 구현**: `data_transmissions` 테이블이 Outbox 역할 수행

### 4.4 TCC (Try-Confirm-Cancel) 패턴

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Payment   │     │    User     │     │   Product   │
│   Service   │     │   Service   │     │   Service   │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       │ ═══ TRY Phase ═══════════════════════════════
       │                   │                   │
       │──── TryDeduct ───▶│                   │
       │     (임시 차감)    │                   │
       │◀─── Reserved ─────│                   │
       │                   │                   │
       │──── TryReduce ────────────────────────▶
       │     (임시 예약)                        │
       │◀─── Reserved ─────────────────────────│
       │                   │                   │
       │ ═══ CONFIRM Phase ═══════════════════════════
       │                   │                   │
       │──── Confirm ─────▶│                   │
       │     (확정)        │                   │
       │                   │                   │
       │──── Confirm ──────────────────────────▶
       │                   │                   │

실패 시 CANCEL Phase:
       │──── Cancel ──────▶│  (예약 취소)      │
       │──── Cancel ───────────────────────────▶
```

**예시: 재고 TCC**
```java
// Try: 임시 예약
UPDATE stocks
SET reserved_quantity = reserved_quantity + 10
WHERE product_id = 1 AND quantity - reserved_quantity >= 10;

// Confirm: 확정
UPDATE stocks
SET quantity = quantity - 10,
    reserved_quantity = reserved_quantity - 10
WHERE product_id = 1;

// Cancel: 취소
UPDATE stocks
SET reserved_quantity = reserved_quantity - 10
WHERE product_id = 1;
```

---

## 5. 패턴별 비교 및 선택 가이드

| 패턴 | 일관성 | 복잡도 | 성능 | 적합한 상황 |
|------|--------|--------|------|-------------|
| 2PC | 강한 일관성 | 높음 | 낮음 | 짧은 트랜잭션, 소수 참여자 |
| SAGA | 최종 일관성 | 중간 | 높음 | 장시간 트랜잭션, 다수 서비스 |
| TCC | 강한 일관성 | 높음 | 중간 | 예약/확정이 자연스러운 도메인 |
| Outbox | 최종 일관성 | 낮음 | 높음 | 이벤트 발행 보장 필요 시 |

### 5.1 이커머스 시스템 권장 조합

```
┌─────────────────────────────────────────────────────────────┐
│                    결제 플로우 설계                           │
│                                                              │
│  1. 핵심 트랜잭션 (동기, 강한 일관성)                         │
│     ├─ 잔액 확인/차감: 단일 DB 트랜잭션                       │
│     └─ 재고 확인/차감: 분산락 + DB 트랜잭션                   │
│                                                              │
│  2. SAGA + Outbox (비동기, 최종 일관성)                       │
│     ├─ 쿠폰 사용 처리                                        │
│     ├─ 포인트 적립                                           │
│     └─ 알림 발송                                             │
│                                                              │
│  3. 이벤트 기반 (비동기, 최종 일관성)                         │
│     ├─ 랭킹 업데이트                                         │
│     ├─ 통계 집계                                             │
│     └─ 외부 플랫폼 전송                                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. 구현 시 고려사항

### 6.1 멱등성 (Idempotency) 보장

```java
// 결제 요청에 고유 키 부여
@Entity
public class Payment {
    @Column(unique = true)
    private String idempotencyKey;  // 클라이언트가 생성한 고유 키
}

// 서비스에서 중복 체크
public Payment processPayment(PaymentRequest request) {
    // 이미 처리된 요청인지 확인
    Optional<Payment> existing = paymentRepository
        .findByIdempotencyKey(request.getIdempotencyKey());

    if (existing.isPresent()) {
        return existing.get();  // 기존 결과 반환 (재처리 방지)
    }

    // 새로운 결제 처리
    return createNewPayment(request);
}
```

### 6.2 보상 트랜잭션 실패 처리

```java
@Component
public class CompensationRetryHandler {

    @Scheduled(fixedDelay = 60000)  // 1분마다 실행
    public void retryFailedCompensations() {
        List<FailedCompensation> failures = compensationRepository
            .findByStatusAndRetriesLessThan("FAILED", MAX_RETRIES);

        for (FailedCompensation failure : failures) {
            try {
                executeCompensation(failure);
                failure.markAsCompleted();
            } catch (Exception e) {
                failure.incrementRetries();
                if (failure.getRetries() >= MAX_RETRIES) {
                    // 수동 개입 필요
                    alertService.notifyManualInterventionRequired(failure);
                }
            }
            compensationRepository.save(failure);
        }
    }
}
```

### 6.3 분산 추적 (Distributed Tracing)

```java
// 모든 서비스 간 호출에 Correlation ID 전파
@Component
public class CorrelationIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        HttpServletRequest request = (HttpServletRequest) req;

        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlationId", correlationId);

        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
```

---

## 7. 결론

### 7.1 현재 시스템 상태
- 모놀리식 구조로 단일 DB 트랜잭션 사용
- ACID 보장으로 데이터 정합성 완벽
- 이벤트 기반 비동기 처리 일부 도입 (STEP 15)

### 7.2 MSA 전환 시 권장 전략

1. **점진적 분리**: 한 번에 모든 서비스를 분리하지 않음
2. **SAGA 패턴 도입**: 결제 플로우에 Orchestration SAGA 적용
3. **Outbox 패턴 확대**: 모든 도메인 이벤트에 적용
4. **멱등성 필수**: 모든 API에 idempotency key 적용
5. **모니터링 강화**: 분산 추적, 보상 트랜잭션 모니터링

### 7.3 트레이드오프 인식

```
강한 일관성 ◀────────────────────▶ 최종 일관성
     │                                    │
     │  - 느린 응답 시간                   │  - 빠른 응답 시간
     │  - 낮은 가용성                      │  - 높은 가용성
     │  - 복잡한 분산 트랜잭션             │  - 보상 트랜잭션 필요
     │                                    │
     └──── 결제 핵심 로직 ─────────────────┘
                    │
                    └──── 부가 기능 (랭킹, 알림, 통계)
```

**핵심 원칙**: 비즈니스 요구사항에 따라 일관성 수준을 선택하고, 그에 맞는 패턴을 적용한다.
