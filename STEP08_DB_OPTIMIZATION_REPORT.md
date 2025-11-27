
# STEP08 - DB 쿼리 및 인덱스 최적화 보고서

## 📊 성능 저하 가능성이 있는 기능 식별

### 1. 선착순 쿠폰 발급 시스템

**분석 대상**: `CouponService.issueCoupon()` 메서드

```sql
-- 현재 쿼리
SELECT * FROM coupons
WHERE id = ? 
AND issued_quantity < total_quantity
AND NOW() BETWEEN start_date AND end_date
FOR UPDATE;

UPDATE coupons 
SET issued_quantity = issued_quantity + 1
WHERE id = ?;
```

**문제점**:
- 동시성 제어를 위한 `FOR UPDATE` 락이 테이블 전체에 걸릴 수 있음
- `issued_quantity < total_quantity` 조건으로 인한 테이블 스캔 발생 가능

### 2. 인기 상품 조회 시스템

**분석 대상**: `ProductRepository.findTopSellingProducts()` 메서드

```sql
-- 현재 쿼리
SELECT p.*, COALESCE(SUM(oi.quantity), 0) as sales_count
FROM products p
LEFT JOIN order_items oi ON p.id = oi.product_id
LEFT JOIN orders o ON oi.order_id = o.id
WHERE p.status = 'ACTIVE'
  AND (o.ordered_at IS NULL OR o.ordered_at BETWEEN ? AND ?)
  AND (o.status IS NULL OR o.status IN ('PAID', 'SHIPPED', 'DELIVERED'))
GROUP BY p.id
ORDER BY sales_count DESC
LIMIT ?;
```

**문제점**:
- 복잡한 JOIN으로 인한 성능 저하
- 매번 실시간으로 계산하여 응답 시간 증가
- 대용량 주문 데이터에서 GROUP BY 연산 비용 증가

### 3. 사용자 잔액 거래 내역 조회

**분석 대상**: `BalanceTransactionRepository.findByUserId()` 메서드

```sql
-- 현재 쿼리
SELECT * FROM balance_transactions 
WHERE user_id = ? 
ORDER BY created_at DESC;
```

**문제점**:
- 페이징 없이 모든 거래 내역 조회
- `created_at` 기반 정렬 시 인덱스 부재로 성능 저하 가능

## 🔍 쿼리 실행계획 분석

### 1. 선착순 쿠폰 발급 쿼리 분석

```sql
EXPLAIN SELECT * FROM coupons
WHERE id = 'coupon-1' 
AND issued_quantity < total_quantity
AND NOW() BETWEEN start_date AND end_date;
```

**예상 실행계획**:
```
+----+-------------+---------+-------+---------------+
| id | select_type | table   | type  | key           |
+----+-------------+---------+-------+---------------+
|  1 | SIMPLE      | coupons | const | PRIMARY       |
+----+-------------+---------+-------+---------------+
```

**분석**: PRIMARY KEY 사용으로 양호하지만, 복합 조건으로 인한 추가 검증 필요

### 2. 인기 상품 조회 쿼리 분석

```sql
EXPLAIN SELECT p.*, COALESCE(SUM(oi.quantity), 0) as sales_count
FROM products p
LEFT JOIN order_items oi ON p.id = oi.product_id
LEFT JOIN orders o ON oi.order_id = o.id
WHERE p.status = 'ACTIVE'
GROUP BY p.id
ORDER BY sales_count DESC
LIMIT 10;
```

**예상 실행계획**:
```
+----+-------------+-------+------+---------------+------+---------+------+------+-----------------+
| id | select_type | table | type | possible_keys | key  | key_len | ref  | rows | Extra           |
+----+-------------+-------+------+---------------+------+---------+------+------+-----------------+
|  1 | SIMPLE      | p     | ALL  | NULL          | NULL | NULL    | NULL | 1000 | Using where     |
|  1 | SIMPLE      | oi    | ALL  | NULL          | NULL | NULL    | NULL | 5000 | Using where     |
|  1 | SIMPLE      | o     | ALL  | NULL          | NULL | NULL    | NULL | 2000 | Using where     |
+----+-------------+-------+------+---------------+------+---------+------+------+-----------------+
```

**분석**: 모든 테이블에서 풀 스캔 발생, 심각한 성능 저하 예상

## 🚀 최적화 솔루션

### 1. 인덱스 설계 개선

#### A. 쿠폰 테이블 복합 인덱스
```sql
-- 선착순 쿠폰 조회 최적화
CREATE INDEX idx_coupons_active_period ON coupons(
    start_date, 
    end_date, 
    issued_quantity, 
    total_quantity
) WHERE start_date <= NOW() AND end_date >= NOW();
```

#### B. 주문 관련 인덱스
```sql
-- 인기 상품 계산용 인덱스
CREATE INDEX idx_orders_status_date ON orders(status, ordered_at);
CREATE INDEX idx_order_items_product_order ON order_items(product_id, order_id);
```

#### C. 거래 내역 조회 인덱스
```sql
-- 사용자별 거래 내역 조회 최적화
CREATE INDEX idx_balance_transactions_user_created ON balance_transactions(user_id, created_at DESC);
```

### 2. 쿼리 구조 개선

#### A. 인기 상품 조회 최적화 (캐시 기반)
```sql
-- 기존 복잡한 JOIN 대신 사전 계산된 캐시 테이블 활용
SELECT p.*, ppc.sales_count, ppc.ranking
FROM products p
JOIN popular_products_cache ppc ON p.id = ppc.product_id
WHERE p.status = 'ACTIVE'
  AND ppc.period_start = ?
  AND ppc.period_end = ?
ORDER BY ppc.ranking
LIMIT ?;
```

#### B. 거래 내역 페이징 쿼리
```sql
-- 페이징 적용으로 성능 개선
SELECT * FROM balance_transactions 
WHERE user_id = ? 
  AND created_at < ?  -- 커서 기반 페이징
ORDER BY created_at DESC
LIMIT ?;
```

### 3. 아키텍처 개선

#### A. 인기 상품 캐시 시스템
- **배치 작업**: 매시간 인기 상품 순위 계산
- **캐시 테이블**: `popular_products_cache` 테이블 활용
- **실시간 업데이트**: Redis를 통한 실시간 랭킹 보완

#### B. 읽기 전용 복제본 활용
```java
@Transactional(readOnly = true)
@DataSource("slave")
public List<Product> findTopSellingProducts() {
    // 읽기 전용 복제본에서 조회
}
```

## 📈 성능 개선 효과 예상

### Before & After 비교

| 기능 | 기존 응답시간 | 개선 후 응답시간 | 개선율 |
|------|---------------|------------------|--------|
| 선착순 쿠폰 발급 | ~500ms | ~50ms | 90% |
| 인기 상품 조회 | ~2000ms | ~100ms | 95% |
| 거래 내역 조회 | ~800ms | ~80ms | 90% |

### 동시성 처리 능력 향상

| 시나리오 | 기존 TPS | 개선 후 TPS | 개선율 |
|----------|----------|-------------|--------|
| 쿠폰 발급 | 100 TPS | 500 TPS | 400% |
| 상품 조회 | 200 TPS | 1000 TPS | 400% |

## 🛠 구현 계획

### Phase 1: 인덱스 최적화 (1주)
- [ ] 핵심 인덱스 생성 및 적용
- [ ] 쿼리 실행계획 검증
- [ ] 성능 테스트 수행

### Phase 2: 캐시 시스템 구축 (2주)
- [ ] 인기 상품 캐시 테이블 구현
- [ ] 배치 작업 스케줄러 개발
- [ ] Redis 캐시 레이어 추가

### Phase 3: 읽기 복제본 적용 (1주)
- [ ] Master-Slave 구성
- [ ] 읽기 전용 쿼리 분리
- [ ] 로드 밸런싱 적용

## 💡 추가 최적화 고려사항

### 1. 데이터 파티셔닝
```sql
-- 거래 내역 월별 파티셔닝
CREATE TABLE balance_transactions (
    -- 컬럼 정의
) PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202401 VALUES LESS THAN (202402),
    PARTITION p202402 VALUES LESS THAN (202403),
    -- ...
);
```

### 2. 연결 풀 최적화
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
      max-lifetime: 1200000
```

### 3. 쿼리 캐시 활용
```java
@Cacheable(value = "popular-products", key = "#period")
public List<Product> getPopularProducts(String period) {
    // 캐시 적용
}
```

## 📊 모니터링 계획

### 성능 지표 추적
- Query execution time
- Database connection pool usage
- Cache hit ratio
- TPS (Transactions Per Second)

### 알림 설정
- 응답시간 > 1초 시 알림
- DB 커넥션 사용률 > 80% 시 알림
- 캐시 적중률 < 80% 시 알림

---

**결론**: 제안된 최적화 방안을 통해 전체적인 시스템 성능을 크게 향상시킬 수 있을 것으로 예상되며, 특히 대용량 트래픽 상황에서의 안정성과 응답성을 확보할 수 있을 것입니다.