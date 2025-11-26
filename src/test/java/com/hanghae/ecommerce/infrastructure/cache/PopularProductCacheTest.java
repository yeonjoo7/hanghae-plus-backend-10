package com.hanghae.ecommerce.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hanghae.ecommerce.config.RedisTestContainerConfig;
import com.hanghae.ecommerce.infrastructure.cache.PopularProductCache.PopularProductInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PopularProductCache Redis 통합 테스트
 * 
 * 전체 Spring Context 없이 Redis만 테스트합니다.
 */
class PopularProductCacheTest {

  private PopularProductCache cache;
  private RedisTemplate<String, Object> redisTemplate;
  private RedisConnectionFactory connectionFactory;

  @BeforeEach
  void setUp() {
    // Testcontainers Redis 시작
    RedisTestContainerConfig.startRedisContainer();

    // Redis 연결 설정
    LettuceConnectionFactory factory = new LettuceConnectionFactory(
        RedisTestContainerConfig.getRedisHost(),
        RedisTestContainerConfig.getRedisPort());
    factory.afterPropertiesSet();
    this.connectionFactory = factory;

    // RedisTemplate 설정
    redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(connectionFactory);

    // ObjectMapper 설정
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // GenericJackson2JsonRedisSerializer 사용 (타입 정보 자동 포함)
    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
    StringRedisSerializer stringSerializer = new StringRedisSerializer();

    redisTemplate.setKeySerializer(stringSerializer);
    redisTemplate.setValueSerializer(serializer);
    redisTemplate.setHashKeySerializer(stringSerializer);
    redisTemplate.setHashValueSerializer(serializer);
    redisTemplate.afterPropertiesSet();

    // PopularProductCache 인스턴스 생성
    cache = new PopularProductCache(redisTemplate);

    // Redis 데이터 초기화
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @AfterEach
  void tearDown() {
    // 테스트 후 정리
    if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
      redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
    if (connectionFactory instanceof LettuceConnectionFactory) {
      ((LettuceConnectionFactory) connectionFactory).destroy();
    }
  }

  @Test
  @DisplayName("판매량 증가 테스트")
  void incrementSales() {
    // given
    Long productId = 1L;
    int quantity = 5;
    LocalDate saleDate = LocalDate.now();

    // when
    cache.incrementSales(productId, quantity, saleDate);

    // then
    Map<Long, Integer> salesCount = cache.getSalesCount(saleDate, saleDate);
    assertThat(salesCount).containsEntry(productId, quantity);
  }

  @Test
  @DisplayName("여러 날짜의 판매량 합산 테스트")
  void getSalesCountMultipleDays() {
    // given
    Long productId = 1L;
    LocalDate day1 = LocalDate.now().minusDays(2);
    LocalDate day2 = LocalDate.now().minusDays(1);
    LocalDate day3 = LocalDate.now();

    cache.incrementSales(productId, 10, day1);
    cache.incrementSales(productId, 20, day2);
    cache.incrementSales(productId, 30, day3);

    // when
    Map<Long, Integer> salesCount = cache.getSalesCount(day1, day3);

    // then
    assertThat(salesCount).containsEntry(productId, 60);
  }

  @Test
  @DisplayName("여러 상품의 판매량 조회 테스트")
  void getSalesCountMultipleProducts() {
    // given
    LocalDate today = LocalDate.now();
    cache.incrementSales(1L, 10, today);
    cache.incrementSales(2L, 20, today);
    cache.incrementSales(3L, 30, today);

    // when
    Map<Long, Integer> salesCount = cache.getSalesCount(today, today);

    // then
    assertThat(salesCount)
        .hasSize(3)
        .containsEntry(1L, 10)
        .containsEntry(2L, 20)
        .containsEntry(3L, 30);
  }

  @Test
  @DisplayName("인기 상품 캐시 저장 및 조회 테스트")
  void cachePopularProducts() {
    // given
    String cacheKey = "popular_3days_5items";
    LocalDate startDate = LocalDate.now().minusDays(2);
    LocalDate endDate = LocalDate.now();

    List<PopularProductInfo> products = List.of(
        new PopularProductInfo(1L, 100, 1, startDate, endDate),
        new PopularProductInfo(2L, 90, 2, startDate, endDate),
        new PopularProductInfo(3L, 80, 3, startDate, endDate));

    // when
    cache.cachePopularProducts(cacheKey, products);
    List<PopularProductInfo> cached = cache.getCachedPopularProducts(cacheKey);

    // then
    assertThat(cached).isNotNull();
    assertThat(cached).hasSize(3);
    assertThat(cached.get(0).getProductId()).isEqualTo(1L);
    assertThat(cached.get(0).getSalesCount()).isEqualTo(100);
    assertThat(cached.get(0).getRank()).isEqualTo(1);
  }

  @Test
  @DisplayName("캐시 무효화 테스트")
  void invalidatePopularProductsCache() {
    // given
    String cacheKey1 = "popular_3days_5items";
    String cacheKey2 = "popular_7days_10items";
    LocalDate startDate = LocalDate.now().minusDays(2);
    LocalDate endDate = LocalDate.now();

    List<PopularProductInfo> products = List.of(
        new PopularProductInfo(1L, 100, 1, startDate, endDate));

    cache.cachePopularProducts(cacheKey1, products);
    cache.cachePopularProducts(cacheKey2, products);

    // when
    cache.invalidatePopularProductsCache();

    // then
    assertThat(cache.getCachedPopularProducts(cacheKey1)).isNull();
    assertThat(cache.getCachedPopularProducts(cacheKey2)).isNull();
  }

  @Test
  @DisplayName("오래된 데이터 정리 테스트")
  void cleanup() {
    // given
    LocalDate oldDate = LocalDate.now().minusDays(40);
    LocalDate recentDate = LocalDate.now().minusDays(5);
    LocalDate cutoffDate = LocalDate.now().minusDays(30);

    cache.incrementSales(1L, 10, oldDate);
    cache.incrementSales(2L, 20, recentDate);

    // when
    cache.cleanup(cutoffDate);

    // then
    Map<Long, Integer> oldSales = cache.getSalesCount(oldDate, oldDate);
    Map<Long, Integer> recentSales = cache.getSalesCount(recentDate, recentDate);

    assertThat(oldSales).isEmpty(); // 오래된 데이터는 삭제됨
    assertThat(recentSales).containsEntry(2L, 20); // 최근 데이터는 유지됨
  }

  @Test
  @DisplayName("캐시 통계 조회 테스트")
  void getCacheStats() {
    // given
    LocalDate today = LocalDate.now();
    cache.incrementSales(1L, 10, today);
    cache.incrementSales(2L, 20, today);

    String cacheKey = "popular_3days_5items";
    List<PopularProductInfo> products = List.of(
        new PopularProductInfo(1L, 100, 1, today, today));
    cache.cachePopularProducts(cacheKey, products);

    // when
    PopularProductCache.CacheStats stats = cache.getCacheStats();

    // then
    assertThat(stats).isNotNull();
    assertThat(stats.getTotalDaysTracked()).isEqualTo(1);
    assertThat(stats.getTotalProductsTracked()).isEqualTo(2);
    assertThat(stats.getTotalCachedQueries()).isEqualTo(1);
  }

  @Test
  @DisplayName("무효한 데이터 입력 시 무시 테스트")
  void incrementSalesWithInvalidData() {
    // given
    LocalDate today = LocalDate.now();

    // when
    cache.incrementSales(null, 10, today);
    cache.incrementSales(1L, 0, today);
    cache.incrementSales(1L, -5, today);
    cache.incrementSales(1L, 10, null);

    // then
    Map<Long, Integer> salesCount = cache.getSalesCount(today, today);
    assertThat(salesCount).isEmpty();
  }

  @Test
  @DisplayName("캐시 키 생성 테스트")
  void createCacheKey() {
    // when
    String key1 = PopularProductCache.createCacheKey(3, 5);
    String key2 = PopularProductCache.createCacheKey(7, 10);

    // then
    assertThat(key1).isEqualTo("popular_3days_5items");
    assertThat(key2).isEqualTo("popular_7days_10items");
  }
}
