# Step 12: Redis 기반 캐싱 전략 보고서

## 1. 개요

이 보고서는 e-commerce 애플리케이션의 조회 성능 최적화를 위한 Redis 기반 캐싱 전략을 문서화합니다.

### 1.1 목표
- 트래픽이 많은 API의 응답 시간 개선
- DB 부하 감소를 통한 시스템 안정성 확보
- Cache Stampede 문제 방지를 통한 대규모 트래픽 대응

### 1.2 기술 스택
- **Redis**: 분산 캐시 저장소
- **Redisson**: Redis 클라이언트 (분산락 지원)
- **Spring Data Redis**: 캐시 추상화

---

## 2. 캐시 적용 구간 분석

### 2.1 API별 조회 쿼리 분석

| API | 엔드포인트 | 조회 쿼리 | 특성 |
|-----|----------|----------|------|
| **인기 상품 조회** | `GET /products/popular` | products + stocks + 집계 쿼리 | 조회 빈도 높음, 계산 비용 큼 |
| **상품 상세 조회** | `GET /products/{id}` | products + stocks | 조회 빈도 높음, 데이터 변경 적음 |
| **상품 목록 조회** | `GET /products?ids=...` | products (N개) + stocks (N개) | 조회 빈도 중간, N+1 쿼리 가능성 |

### 2.2 캐시 적합성 평가

| 구간 | 조회 빈도 | 데이터 변경 | 캐시 적합도 |
|------|----------|------------|------------|
| 인기 상품 | 매우 높음 | 낮음 | 최적 |
| 상품 상세 | 높음 | 낮음 | 적합 |
| 상품+재고 | 높음 | 중간 | 조건부 |
| 사용자 정보 | 중간 | 중간 | 부적합 |
| 주문 정보 | 낮음 | 높음 | 부적합 |

### 2.3 대량 트래픽 시 지연 발생 가능 쿼리

#### 인기 상품 조회 쿼리
```sql
-- 기간 내 상품별 판매량 집계 (가장 비용이 큰 쿼리)
SELECT product_id, SUM(quantity) as total_sales
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE o.state = 'COMPLETED'
  AND o.created_at BETWEEN :startDate AND :endDate
GROUP BY product_id
ORDER BY total_sales DESC
LIMIT :limit;
```

**문제점:**
1. 주문 테이블과 주문 아이템 테이블 JOIN
2. 대량 데이터 집계 (GROUP BY + ORDER BY)
3. 날짜 범위 검색 (인덱스 활용 어려울 수 있음)

#### 상품 상세 조회 쿼리
```sql
-- 상품 조회
SELECT * FROM products WHERE id = :productId;

-- 재고 조회
SELECT * FROM stocks WHERE product_id = :productId AND product_option_id IS NULL;
```

**문제점:**
1. 2번의 DB 왕복
2. 인기 상품의 경우 동시 조회 집중

---

## 3. 캐싱 전략 설계

### 3.1 적용된 캐시 패턴

#### Cache Aside (Lazy Loading) 패턴

**적용 이유:**
- 읽기가 쓰기보다 훨씬 많은 조회 API에 적합
- 필요한 데이터만 캐시하여 메모리 효율적
- 캐시 미스 시에만 DB 조회

### 3.2 캐시별 TTL 전략

| 캐시 종류 | TTL | 설정 근거 |
|----------|-----|----------|
| **인기 상품** | 5분 | 실시간성 중요, 하지만 집계 비용 감소 필요 |
| **상품 상세** | 30분 | 상품 정보는 자주 변경되지 않음 |
| **상품+재고** | 5분 | 재고는 주문 시 변경되므로 짧은 TTL |

### 3.3 캐시 키 설계

| 용도 | 캐시 키 패턴 | 예시 |
|------|-------------|------|
| 인기 상품 | `popular-products:{days}days_{limit}items` | `popular-products:3days_5items` |
| 상품 상세 | `product:{productId}` | `product:123` |
| 상품+재고 | `product-with-stock:{productId}` | `product-with-stock:123` |

---

## 4. Cache Stampede 방지 전략

### 4.1 Cache Stampede란?

캐시 만료 시점에 동시에 많은 요청이 DB로 향하는 현상입니다.

**정상 상황:**
- User A → Cache HIT → 즉시 응답

**Cache Stampede (캐시 만료 시점):**
- User A, B, C, D... → Cache MISS → 동시에 DB 조회 → DB 과부하!

### 4.2 구현된 방지 전략: 분산락 기반 캐시 갱신

```java
public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
    // 1. 캐시에서 먼저 조회 (Cache Hit)
    T cachedValue = get(key, type);
    if (cachedValue != null) {
        return cachedValue;
    }

    // 2. 캐시 미스 - 분산락을 사용하여 DB 조회 (Cache Stampede 방지)
    String lockKey = CACHE_LOCK_PREFIX + key;
    
    return lockManager.executeWithLock(lockKey, CACHE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> {
        // 3. 이중 체크 - 다른 스레드가 이미 캐시를 갱신했을 수 있음
        T doubleCheckValue = get(key, type);
        if (doubleCheckValue != null) {
            return doubleCheckValue;
        }

        // 4. DB에서 조회
        T loadedValue = loader.get();
        
        // 5. 캐시에 저장
        if (loadedValue != null) {
            set(key, loadedValue, ttl);
        }
        
        return loadedValue;
    });
}
```

### 4.3 방지 전략 흐름

1. **User A** → Cache MISS → Lock 획득 → DB 조회 → 캐시 저장 → 응답
2. **User B** → Cache MISS → Lock 대기... → (Lock 획득 후) → Cache HIT! → 응답
3. **User C** → Cache MISS → Lock 대기... → (Lock 획득 후) → Cache HIT! → 응답

**결과: DB 조회는 1번만 발생!**

### 4.4 적용된 전략 요약

| 전략 | 설명 | 적용 여부 |
|------|------|----------|
| **분산락 + Double Check** | 락 획득 후 캐시 재확인 | 적용 |
| **Probabilistic Early Expiration** | 만료 전 일정 확률로 갱신 | 미적용 |
| **Stale-While-Revalidate** | 만료된 캐시 반환 + 비동기 갱신 | 미적용 |
| **Cache Warming** | 스케줄러로 미리 캐시 워밍업 | 적용 |

---

## 5. 캐시 무효화 전략

### 5.1 무효화 시점

| 이벤트 | 무효화 대상 |
|--------|-----------|
| 상품 정보 수정 | `product:{id}`, `product-with-stock:{id}` |
| 재고 변경 | `product-with-stock:{id}` |
| 주문 완료 | `popular-products:*` |

### 5.2 구현 코드

```java
// 인기 상품 캐시 무효화 (판매 기록 시)
public void recordSale(Long productId, int quantity, LocalDate saleDate) {
    cache.incrementSales(productId, quantity, recordDate);
    
    // 판매 기록 시 Redis 캐시 무효화
    redisCacheService.deleteByPattern(REDIS_CACHE_PREFIX + "*");
}
```

---

## 6. 성능 개선 예상 효과

### 6.1 응답 시간 개선

| 구간 | 캐시 미적용 | 캐시 적용 | 개선율 |
|------|------------|----------|--------|
| 인기 상품 조회 | ~200ms | ~5ms | **97.5%** |
| 상품 상세 조회 | ~50ms | ~3ms | **94%** |
| 상품+재고 조회 | ~80ms | ~5ms | **93.75%** |

### 6.2 DB 부하 감소

**캐시 적용 전:**
- 인기 상품 API: 100 req/s → 100 DB queries/s

**캐시 적용 후 (TTL 5분, 히트율 99%):**
- 인기 상품 API: 100 req/s → 1 DB query / 5분

**DB 부하 감소율: 99.997%**

### 6.3 Cache Hit Rate 목표

| 캐시 | 목표 히트율 | 근거 |
|------|-----------|------|
| 인기 상품 | 99%+ | TTL 내 동일 결과, 갱신 적음 |
| 상품 상세 | 95%+ | 상품 정보 변경 빈도 낮음 |
| 상품+재고 | 85%+ | 재고 변경 시 무효화 |

---

## 7. 구현 상세

### 7.1 파일 구조

```
src/main/java/com/hanghae/ecommerce/
├── infrastructure/
│   ├── cache/
│   │   ├── PopularProductCache.java     # 인기 상품 판매량 집계 (in-memory)
│   │   └── RedisCacheService.java       # Redis 캐시 서비스 (Cache Stampede 방지)
│   └── config/
│       ├── RedisConfig.java             # Redisson 설정
│       └── RedisCacheConfig.java        # Spring Cache + Redis 설정
└── application/
    └── product/
        ├── ProductService.java          # 상품 조회 (캐싱 적용)
        └── PopularProductService.java   # 인기 상품 조회 (캐싱 적용)

src/test/java/com/hanghae/ecommerce/
└── cache/
    ├── PopularProductCacheTest.java     # 인기 상품 캐싱 통합 테스트
    └── RedisCacheServiceTest.java       # RedisCacheService 단위 테스트
```

### 7.2 Redis 캐시 설정

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {
    private static final Duration POPULAR_PRODUCTS_TTL = Duration.ofMinutes(5);
    private static final Duration PRODUCT_TTL = Duration.ofMinutes(30);
    private static final Duration PRODUCT_WITH_STOCK_TTL = Duration.ofMinutes(5);
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(POPULAR_PRODUCTS_CACHE, defaultConfig.entryTtl(POPULAR_PRODUCTS_TTL));
        cacheConfigurations.put(PRODUCT_CACHE, defaultConfig.entryTtl(PRODUCT_TTL));
        cacheConfigurations.put(PRODUCT_WITH_STOCK_CACHE, defaultConfig.entryTtl(PRODUCT_WITH_STOCK_TTL));
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
```

---

## 8. 테스트 검증

### 8.1 테스트 환경 구성

통합 테스트는 **Testcontainers**를 사용하여 실제 Redis 환경에서 실행됩니다.

```java
public abstract class BaseIntegrationTest {

  /**
   * Singleton Redis 컨테이너
   * 모든 테스트 클래스에서 동일한 컨테이너를 공유합니다.
   */
  private static final GenericContainer<?> REDIS_CONTAINER;

  static {
    REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
    REDIS_CONTAINER.start();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", REDIS_CONTAINER::getFirstMappedPort);
  }
}
```

**Singleton 패턴 사용 이유:**
- Spring 컨텍스트 캐싱과 호환
- 테스트 클래스 간 동일한 Redis 컨테이너 공유로 테스트 속도 향상
- `@Container` 어노테이션 사용 시 발생하는 포트 불일치 문제 해결

### 8.2 테스트 파일 구성

| 테스트 파일 | 테스트 목적 |
|------------|-----------|
| `PopularProductCacheTest.java` | 인기 상품 캐싱 통합 테스트 |
| `RedisCacheServiceTest.java` | RedisCacheService 단위 테스트 (Cache Stampede 집중) |

### 8.3 인기 상품 캐싱 테스트 (`PopularProductCacheTest.java`)

| 테스트 | 검증 내용 |
|--------|----------|
| `testCacheHitAfterMiss` | 캐시 미스 → DB 조회 → 캐시 저장 → 캐시 히트 확인 |
| `testCacheStampedePrevention` | 50개 동시 요청 시 모든 요청 성공 |
| `testCacheInvalidation` | 판매 기록 시 캐시 무효화 확인 |
| `testDifferentCacheKeys` | 다른 조회 조건은 다른 캐시 키 사용 |
| `testHighConcurrencyPerformance` | 100개 동시 요청 성능 및 정합성 |
| `testCacheTtl` | 캐시 TTL이 5분으로 설정되었는지 확인 |

### 8.4 Cache Stampede 방지 테스트 (`RedisCacheServiceTest.java`)

| 테스트 | 검증 내용 |
|--------|----------|
| `testBasicCacheOperations` | 기본 캐시 저장/조회 |
| `testGetOrLoadCacheMiss` | 캐시 미스 시 로더 호출 |
| `testGetOrLoadCacheHit` | 캐시 히트 시 로더 미호출 |
| `testCacheStampedePrevention` | **50개 동시 요청 시 로더가 정확히 1번만 호출되는지 검증** |
| `testDoubleCheckLocking` | **100개 동시 요청에서 Double-Check 동작 확인** |

### 8.5 핵심 테스트 코드: Cache Stampede 방지 검증

```java
@Test
@DisplayName("Cache Stampede 방지 - 동시 요청 시 로더가 1번만 호출됨")
void testCacheStampedePrevention() throws InterruptedException {
    int concurrentRequests = 50;
    ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);
    AtomicInteger loaderCallCount = new AtomicInteger(0);

    // 50개 요청이 동시에 시작
    for (int i = 0; i < concurrentRequests; i++) {
        executor.submit(() -> {
            try {
                startLatch.await();  // 모든 스레드가 동시에 시작
                redisCacheService.getOrLoad(key, TestData.class, ttl, () -> {
                    loaderCallCount.incrementAndGet();  // 로더 호출 카운트
                    return new TestData("value", 100);
                });
            } finally {
                doneLatch.countDown();
            }
        });
    }

    startLatch.countDown();  // 모든 스레드 동시 시작
    doneLatch.await(30, TimeUnit.SECONDS);

    // 핵심 검증: 로더는 정확히 1번만 호출되어야 함!
    assertThat(loaderCallCount.get())
        .as("Cache Stampede 방지: 로더는 1번만 호출되어야 함")
        .isEqualTo(1);
}
```

### 8.6 테스트 실행 예시 출력

```
=== Cache Stampede 방지 테스트 결과 ===
동시 요청 수: 50
로더 호출 횟수: 1 (기대값: 1)
성공한 요청: 50

=== Double-Check Locking 테스트 결과 ===
동시 요청 수: 100
로더 호출 횟수: 1
일관된 결과 수: 100
```

---

## 9. 모니터링 및 운영

### 9.1 모니터링 포인트

- **Cache Hit/Miss Rate**: 캐시 효율성 측정
- **Cache Memory Usage**: Redis 메모리 사용량
- **DB Query Count**: 캐시 적용 전후 비교
- **API Response Time**: P50, P95, P99 지표

### 9.2 운영 고려사항

1. **캐시 워밍업**: 서버 시작 시 주요 캐시 미리 로드
2. **캐시 모니터링**: Redis Insight 또는 Grafana 대시보드
3. **장애 대응**: Redis 장애 시 DB 직접 조회 폴백
4. **메모리 관리**: TTL 및 최대 크기 설정으로 메모리 관리

---

## 10. 결론

### 10.1 적용된 캐싱 전략 요약

| 항목 | 전략 |
|------|------|
| 캐시 패턴 | Cache Aside (Lazy Loading) |
| 캐시 저장소 | Redis (Redisson) |
| TTL 전략 | 데이터 특성별 차별화 (5분 ~ 30분) |
| Cache Stampede 방지 | 분산락 + Double Check |
| 캐시 무효화 | 이벤트 기반 (Write-Through) |

### 10.2 기대 효과

- **응답 시간**: 90% 이상 개선
- **DB 부하**: 99% 이상 감소
- **시스템 안정성**: 대규모 트래픽 대응 가능

### 10.3 향후 개선 방향

1. **캐시 히트율 모니터링 대시보드** 구축
2. **Probabilistic Early Expiration** 적용 검토
3. **Local Cache + Redis 2-tier 캐시** 구조 검토
4. **캐시 프리워밍 스케줄러** 고도화





