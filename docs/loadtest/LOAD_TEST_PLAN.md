# 부하 테스트 계획서

## 1. 개요

### 1.1 목적
본 문서는 이커머스 시스템의 핵심 기능에 대한 부하 테스트 계획을 정의합니다.
주요 목표는 시스템의 성능 한계를 파악하고, 병목 지점을 식별하여 개선하는 것입니다.

### 1.2 테스트 범위
- **쿠폰 발급 시나리오**: 선착순 쿠폰 발급 시 동시성 처리 성능
- **주문/결제 플로우**: E2E 구매 프로세스의 처리량 및 안정성

### 1.3 테스트 도구
- **k6**: 오픈소스 부하 테스트 도구
- **Docker**: 테스트 환경 구성
- **Grafana/InfluxDB**: 메트릭 시각화 (선택사항)

---

## 2. 테스트 환경

### 2.1 시스템 구성
```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   k6 CLI    │────▶│  App Server │────▶│   MySQL     │
│  (Client)   │     │  (Spring)   │     │   8.0       │
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
                    ┌──────▼──────┐
                    │    Kafka    │
                    │   3.7.0     │
                    └─────────────┘
```

### 2.2 리소스 사양

| 컴포넌트 | CPU | Memory | 비고 |
|---------|-----|--------|------|
| App Server | 2 cores | 2GB | Spring Boot |
| MySQL | 1 core | 1GB | 데이터베이스 |
| Kafka | 1 core | 1GB | 메시지 브로커 |
| k6 | 2 cores | 2GB | 부하 생성 |

### 2.3 테스트 데이터
- **사용자**: 1,000명
- **상품**: 5종 (각 100,000개 재고)
- **쿠폰**: 3종 (선착순 100개 포함)

---

## 3. 테스트 시나리오

### 3.1 시나리오 1: 쿠폰 발급 Spike Test

#### 목적
선착순 100개 쿠폰에 1,000명 이상의 동시 요청 시 시스템 동작 검증

#### 대상 API
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/coupons/{couponId}/issue` | 동기 쿠폰 발급 |
| POST | `/coupons/{couponId}/request` | 비동기 쿠폰 발급 요청 |

#### 부하 패턴
```
VUs
1000 ┤     ┌──────────────────────┐
     │    /                        \
     │   /                          \
   0 ┼──/                            \──
     0  10s                    40s   50s
```

#### 테스트 설정
```javascript
stages: [
  { duration: '10s', target: 1000 },  // 급증
  { duration: '30s', target: 1000 },  // 유지
  { duration: '10s', target: 0 },     // 감소
]
```

#### 성공 기준
| 지표 | 목표값 |
|------|--------|
| 응답 시간 (p95) | < 500ms |
| 에러율 | < 1% |
| 쿠폰 초과 발급 | 0건 |

---

### 3.2 시나리오 2: 주문/결제 플로우 Load Test

#### 목적
E2E 구매 프로세스의 처리량 및 트랜잭션 안정성 검증

#### 대상 API
| 순서 | Method | Endpoint | 설명 |
|------|--------|----------|------|
| 1 | POST | `/balance/charge` | 잔액 충전 |
| 2 | GET | `/products/{productId}` | 상품 조회 |
| 3 | POST | `/carts/items` | 장바구니 추가 |
| 4 | POST | `/orders` | 주문 생성 |
| 5 | POST | `/orders/{orderId}/payment` | 결제 처리 |

#### 부하 패턴
```
VUs
1000 ┤                    ┌──────────┐
 500 ┤          ┌─────────┘          │
 100 ┤    ┌─────┘                    │
   0 ┼────┘                          └──
     0   1m    3m         5m        6m
```

#### 테스트 설정
```javascript
stages: [
  { duration: '1m', target: 100 },    // 램프업
  { duration: '2m', target: 500 },    // 중간 부하
  { duration: '2m', target: 1000 },   // 피크 부하
  { duration: '1m', target: 0 },      // 램프다운
]
```

#### 성공 기준
| 지표 | 목표값 |
|------|--------|
| 응답 시간 (p95) | < 500ms |
| 전체 플로우 (p95) | < 3000ms |
| 에러율 | < 1% |
| 처리량 | > 500 TPS |

---

## 4. 테스트 실행 절차

### 4.1 사전 준비

#### Step 1: 환경 구성
```bash
# Docker 컨테이너 실행
docker-compose up -d

# 헬스 체크
curl http://localhost:8080/actuator/health
```

#### Step 2: 테스트 데이터 생성
```bash
# 테스트 데이터 SQL 실행
mysql -u ecommerce -p ecommerce < loadtest/data/init-loadtest-data.sql
```

#### Step 3: k6 설치 확인
```bash
# k6 버전 확인
k6 version
```

### 4.2 테스트 실행

#### Phase 1: Smoke Test (기본 검증)
```bash
k6 run loadtest/scripts/smoke-test.js
```
- 10 VUs, 1분
- 목적: API 정상 동작 확인

#### Phase 2: Load Test (주문/결제)
```bash
k6 run loadtest/scripts/order-flow-test.js
```
- 100 → 500 → 1000 VUs
- 목적: 처리량 및 안정성 검증

#### Phase 3: Spike Test (쿠폰 발급)
```bash
k6 run loadtest/scripts/coupon-spike-test.js
```
- 1000 VUs 급증
- 목적: 선착순 처리 검증

### 4.3 결과 수집
```bash
# 결과 파일 위치
ls -la loadtest/results/
```

---

## 5. 측정 지표

### 5.1 핵심 지표 (KPIs)

| 지표 | 설명 | 측정 방법 |
|------|------|----------|
| **TPS** | 초당 트랜잭션 수 | `http_reqs.rate` |
| **Response Time** | 응답 시간 | `http_req_duration` |
| **Error Rate** | 에러율 | `http_req_failed` |
| **Throughput** | 처리량 | `data_received` |

### 5.2 비즈니스 지표

| 지표 | 설명 |
|------|------|
| `coupons_issued` | 발급된 쿠폰 수 |
| `orders_created` | 생성된 주문 수 |
| `payment_success` | 결제 성공 건수 |
| `order_flow_duration` | 전체 플로우 소요 시간 |

### 5.3 목표 기준

| 테스트 유형 | p50 | p95 | p99 | Error Rate |
|------------|-----|-----|-----|------------|
| Smoke | <100ms | <200ms | <500ms | <0.1% |
| Load | <200ms | <500ms | <1000ms | <1% |
| Spike | <500ms | <1000ms | <2000ms | <5% |

---

## 6. 예상 병목 지점

### 6.1 데이터베이스
- **Connection Pool 고갈**: 동시 요청 급증 시
- **Lock Contention**: 쿠폰/재고 동시 업데이트 시
- **Slow Query**: 복잡한 조회 쿼리

### 6.2 애플리케이션
- **Thread Pool 고갈**: 동시 처리 요청 초과
- **Memory 부족**: 대용량 객체 생성
- **GC Pause**: Full GC 발생 시 지연

### 6.3 Kafka
- **Consumer Lag**: 처리 속도 < 생산 속도
- **Partition 불균형**: 특정 파티션 과부하

---

## 7. 테스트 일정

| 단계 | 활동 | 소요 시간 |
|------|------|----------|
| 1 | 환경 구성 및 데이터 준비 | 30분 |
| 2 | Smoke Test 실행 | 5분 |
| 3 | Load Test 실행 | 10분 |
| 4 | Spike Test 실행 | 5분 |
| 5 | 결과 분석 및 보고서 작성 | 1시간 |

---

## 8. 리스크 및 대응

| 리스크 | 영향도 | 대응 방안 |
|--------|--------|----------|
| 테스트 환경 불안정 | 높음 | Docker 재시작, 리소스 확인 |
| 데이터 불일치 | 중간 | 테스트 전 데이터 초기화 |
| 시스템 다운 | 높음 | 모니터링 강화, 자동 복구 설정 |

---

## 9. 참고 자료

- [k6 공식 문서](https://k6.io/docs/)
- [성능 테스트 베스트 프랙티스](https://grafana.com/load-testing/)
- 프로젝트 API 명세서: `docs/api/api-specification.md`
