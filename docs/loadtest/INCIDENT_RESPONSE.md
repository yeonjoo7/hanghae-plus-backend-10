# 장애 대응 문서

## 1. 개요

본 문서는 이커머스 시스템에서 발생할 수 있는 주요 장애 시나리오와 대응 절차를 정의합니다.
부하 테스트에서 예상되는 장애 상황을 기반으로 작성되었습니다.

---

## 2. 장애 등급 정의

| 등급 | 명칭 | 정의 | 대응 시간 |
|------|------|------|----------|
| P1 | Critical | 전체 서비스 중단 | 즉시 대응 (15분 이내) |
| P2 | High | 핵심 기능 장애 (결제, 주문 불가) | 30분 이내 |
| P3 | Medium | 부분 기능 장애 (일부 API 오류) | 2시간 이내 |
| P4 | Low | 성능 저하 (응답 지연) | 4시간 이내 |

---

## 3. 장애 시나리오 및 대응

### 3.1 데이터베이스 연결 풀 고갈

#### 증상
- API 응답 시간 급격히 증가 (수 초 이상)
- 에러 로그: `Connection pool exhausted`, `Cannot acquire connection`
- 새로운 요청 처리 불가

#### 원인
- 동시 요청 수가 DB 커넥션 풀 크기 초과
- 트랜잭션이 오래 유지됨 (락 대기)
- 커넥션 누수

#### 영향도
- **등급**: P1 (Critical)
- **영향 범위**: 전체 서비스

#### 대응 절차

```
1. 현황 파악 (5분)
   - DB 커넥션 수 확인
   - 애플리케이션 로그 확인
   - 활성 트랜잭션 확인

2. 긴급 조치 (10분)
   - 불필요한 커넥션 강제 종료
   - 커넥션 풀 크기 동적 증가 (가능한 경우)
   - 트래픽 제한 (Rate Limiting)

3. 근본 해결 (이후)
   - 커넥션 풀 크기 적정화
   - 트랜잭션 최적화
   - 커넥션 타임아웃 설정 검토
```

#### 모니터링 명령어
```sql
-- MySQL 활성 커넥션 확인
SHOW PROCESSLIST;

-- 커넥션 통계
SHOW STATUS LIKE 'Threads_connected';
SHOW STATUS LIKE 'Max_used_connections';
```

#### 예방 대책
- Connection Pool 모니터링 알람 설정
- 적절한 커넥션 풀 크기 설정 (기본: 10, 최대: 50)
- 커넥션 validation 설정

---

### 3.2 쿠폰 초과 발급 (동시성 이슈)

#### 증상
- 선착순 100개 쿠폰이 100개 이상 발급됨
- 동일 사용자에게 중복 발급됨
- 데이터 정합성 불일치

#### 원인
- 동시성 제어 미흡 (Race Condition)
- 락 획득 실패 후 재시도 로직 오류
- 분산 환경에서 락 동기화 실패

#### 영향도
- **등급**: P2 (High)
- **영향 범위**: 쿠폰 발급 기능

#### 대응 절차

```
1. 현황 파악 (10분)
   - 실제 발급된 쿠폰 수 확인
   - 중복 발급 사용자 식별
   - 발급 시간대 분석

2. 긴급 조치 (30분)
   - 쿠폰 발급 API 일시 중단
   - 초과 발급된 쿠폰 무효화 처리
   - 중복 발급 사용자 알림

3. 데이터 정합성 복구 (이후)
   - 정상 발급분 식별 (선착순 기준)
   - 초과분 삭제 또는 무효화
   - 쿠폰 수량 카운트 재설정
```

#### 복구 SQL 예시
```sql
-- 초과 발급 확인
SELECT coupon_id, COUNT(*) as issued_count, total_quantity
FROM coupons c
JOIN user_coupons uc ON c.id = uc.coupon_id
GROUP BY coupon_id
HAVING issued_count > total_quantity;

-- 초과 발급분 무효화 (최신 발급분부터)
UPDATE user_coupons
SET status = 'INVALIDATED'
WHERE coupon_id = :couponId
  AND id NOT IN (
    SELECT id FROM (
      SELECT id FROM user_coupons
      WHERE coupon_id = :couponId
      ORDER BY created_at ASC
      LIMIT :totalQuantity
    ) as valid_coupons
  );
```

#### 예방 대책
- 분산 락 도입 (Redis/Redisson)
- 비관적 락 사용
- 발급 전 재고 확인 로직 강화

---

### 3.3 Kafka Consumer Lag 증가

#### 증상
- 쿠폰 발급 요청 후 실제 발급까지 지연
- Consumer Lag 지속적으로 증가
- 메시지 처리 속도 저하

#### 원인
- Consumer 처리 속도 < Producer 생산 속도
- Consumer 장애 또는 재시작
- 파티션 불균형

#### 영향도
- **등급**: P3 (Medium)
- **영향 범위**: 비동기 쿠폰 발급

#### 대응 절차

```
1. 현황 파악 (10분)
   - Kafka Consumer Lag 확인
   - Consumer 상태 확인
   - 토픽 파티션 분포 확인

2. 긴급 조치 (20분)
   - Consumer 인스턴스 수 증가
   - Consumer 재시작 (필요시)
   - 파티션 리밸런싱

3. 근본 해결 (이후)
   - Consumer 처리 로직 최적화
   - 파티션 수 조정
   - 배치 처리 도입
```

#### Kafka 명령어
```bash
# Consumer Lag 확인
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group coupon-issuer-group

# 토픽 상태 확인
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic coupon-issuance-request
```

#### 예방 대책
- Consumer Lag 모니터링 알람
- Auto Scaling 설정
- Dead Letter Topic (DLT) 활용

---

### 3.4 재고 음수 발생 (재고 정합성 오류)

#### 증상
- 상품 재고가 음수로 표시됨
- 주문 완료 후 재고 불일치
- 재고 차감 실패 로그

#### 원인
- 동시 주문 시 재고 차감 경쟁
- 락 획득 순서 문제
- 롤백 처리 누락

#### 영향도
- **등급**: P2 (High)
- **영향 범위**: 주문/재고 관리

#### 대응 절차

```
1. 현황 파악 (10분)
   - 음수 재고 상품 식별
   - 관련 주문 내역 확인
   - 재고 변동 이력 분석

2. 긴급 조치 (20분)
   - 해당 상품 주문 일시 중단
   - 재고 수량 수동 보정
   - 비정상 주문 검토

3. 데이터 정합성 복구 (이후)
   - 실제 재고 수량 확인 (물리적 재고)
   - DB 재고 수량 보정
   - 영향받은 주문 처리 (취소/보상)
```

#### 복구 SQL 예시
```sql
-- 음수 재고 확인
SELECT * FROM stocks WHERE quantity < 0;

-- 재고 보정 (0으로 설정)
UPDATE stocks SET quantity = 0 WHERE quantity < 0;

-- 재고 변동 이력 확인
SELECT * FROM stock_history
WHERE product_id = :productId
ORDER BY created_at DESC
LIMIT 100;
```

#### 예방 대책
- 재고 차감 시 CHECK 제약 조건
- 비관적 락 적용
- 재고 변동 이력 테이블 관리

---

### 3.5 결제 실패 급증

#### 증상
- 결제 성공률 급격히 하락
- 결제 API 응답 지연
- 사용자 포인트 차감 후 결제 실패

#### 원인
- 포인트 잔액 부족
- 동시 결제 시 충돌
- 트랜잭션 타임아웃

#### 영향도
- **등급**: P1 (Critical)
- **영향 범위**: 결제 기능

#### 대응 절차

```
1. 현황 파악 (5분)
   - 결제 실패 로그 확인
   - 에러 유형 분류
   - 영향 주문 수 파악

2. 긴급 조치 (15분)
   - 결제 재시도 안내 (사용자)
   - 포인트 복구 처리
   - 주문 상태 보정

3. 보상 처리 (이후)
   - 결제 실패 주문 목록 추출
   - 포인트 정합성 확인
   - 사용자 보상 처리
```

#### 복구 SQL 예시
```sql
-- 결제 실패 주문 확인
SELECT o.*, p.status as payment_status
FROM orders o
LEFT JOIN payments p ON o.id = p.order_id
WHERE o.status = 'PENDING_PAYMENT'
  AND o.created_at < DATE_SUB(NOW(), INTERVAL 1 HOUR);

-- 포인트 정합성 확인
SELECT u.id, u.balance,
       (SELECT SUM(amount) FROM balance_transactions WHERE user_id = u.id) as calculated
FROM users u
HAVING u.balance != calculated;
```

---

## 4. 공통 대응 프로세스

### 4.1 장애 발생 시 커뮤니케이션

```
1. 장애 감지 즉시 담당자 알림
2. 장애 등급 판정
3. 슬랙/메일로 관련자 공유
4. 대응 진행 상황 주기적 업데이트
5. 장애 해결 후 사후 보고서 작성
```

### 4.2 장애 보고 템플릿

```markdown
## 장애 보고서

### 개요
- 발생 시간:
- 해결 시간:
- 장애 등급:
- 영향 범위:

### 증상
-

### 원인
-

### 대응 내용
1.
2.
3.

### 재발 방지 대책
-

### 교훈 (Lessons Learned)
-
```

---

## 5. 모니터링 체크리스트

### 5.1 일일 점검 항목

| 항목 | 정상 범위 | 확인 방법 |
|------|----------|----------|
| API 응답 시간 (p95) | < 500ms | Grafana/APM |
| 에러율 | < 0.1% | 로그 분석 |
| DB 커넥션 수 | < 80% | MySQL 모니터링 |
| Kafka Consumer Lag | < 1000 | Kafka UI |
| JVM Heap 사용률 | < 70% | JMX/Actuator |

### 5.2 알람 설정

| 지표 | Warning | Critical |
|------|---------|----------|
| API 응답 시간 | > 1s | > 3s |
| 에러율 | > 1% | > 5% |
| CPU 사용률 | > 70% | > 90% |
| Memory 사용률 | > 70% | > 90% |
| DB 커넥션 | > 70% | > 90% |

---

## 6. 연락처

| 역할 | 담당자 | 연락처 |
|------|--------|--------|
| 1차 대응 | DevOps 팀 | - |
| 2차 대응 | 백엔드 개발팀 | - |
| 3차 대응 (에스컬레이션) | 기술 리더 | - |

---

## 7. 부록

### 7.1 유용한 명령어

```bash
# 애플리케이션 로그 확인
docker logs -f ecommerce-app --tail 1000

# MySQL 슬로우 쿼리 확인
docker exec ecommerce-mysql mysql -u root -p -e "SHOW PROCESSLIST"

# Kafka 토픽 확인
docker exec ecommerce-kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# 시스템 리소스 확인
docker stats
```

### 7.2 참고 문서
- API 명세서: `docs/api/api-specification.md`
- 부하 테스트 계획: `docs/loadtest/LOAD_TEST_PLAN.md`
- 부하 테스트 결과: `docs/loadtest/LOAD_TEST_RESULT.md`
