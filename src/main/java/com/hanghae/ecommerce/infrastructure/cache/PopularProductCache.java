package com.hanghae.ecommerce.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 인기 상품 캐싱 시스템 (Redis 기반)
 * 
 * Redis를 사용하여 인기 상품 조회 성능을 최적화하고,
 * 실시간 판매량 카운터를 관리합니다.
 */
@Component
public class PopularProductCache {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 키 프리픽스
    private static final String DAILY_SALES_PREFIX = "sales:daily:";
    private static final String POPULAR_PRODUCTS_PREFIX = "popular:products:";

    // TTL 설정
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration DAILY_SALES_TTL = Duration.ofDays(30);

    public PopularProductCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 상품 판매량 증가
     * 
     * @param productId 상품 ID
     * @param quantity  판매 수량
     * @param saleDate  판매일
     */
    public void incrementSales(Long productId, int quantity, LocalDate saleDate) {
        if (productId == null || saleDate == null || quantity <= 0) {
            return; // 무효한 데이터는 무시
        }

        String key = DAILY_SALES_PREFIX + saleDate.toString();

        // Redis Hash에 판매량 증가
        redisTemplate.opsForHash().increment(key, productId.toString(), quantity);

        // 30일 후 자동 삭제 (메모리 관리)
        redisTemplate.expire(key, DAILY_SALES_TTL);

        // 캐시 무효화 (새로운 판매 데이터로 인기 상품 순위 변경 가능)
        invalidatePopularProductsCache();
    }

    /**
     * 지정된 기간의 상품별 판매량 조회
     * 
     * @param startDate 시작일 (포함)
     * @param endDate   종료일 (포함)
     * @return 상품별 판매량 맵 (ProductId -> TotalSalesCount)
     */
    public Map<Long, Integer> getSalesCount(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return Map.of();
        }

        Map<Long, Integer> result = new HashMap<>();

        // 기간 내 모든 날짜의 판매량을 합산
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            String key = DAILY_SALES_PREFIX + currentDate.toString();

            // Redis Hash에서 해당 날짜의 모든 판매 데이터 조회
            Map<Object, Object> dailyData = redisTemplate.opsForHash().entries(key);

            if (dailyData != null && !dailyData.isEmpty()) {
                for (Map.Entry<Object, Object> entry : dailyData.entrySet()) {
                    try {
                        Long productId = Long.parseLong(entry.getKey().toString());
                        Integer count = Integer.parseInt(entry.getValue().toString());
                        result.merge(productId, count, Integer::sum);
                    } catch (NumberFormatException e) {
                        // 잘못된 데이터는 무시
                        System.err.println("Invalid sales data: " + entry.getKey() + " = " + entry.getValue());
                    }
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        return result;
    }

    /**
     * 인기 상품 목록 캐시에서 조회
     * 
     * @param cacheKey 캐시 키 (예: "popular_3days_5items")
     * @return 캐시된 인기 상품 목록, 없으면 null
     */
    @SuppressWarnings("unchecked")
    public List<PopularProductInfo> getCachedPopularProducts(String cacheKey) {
        if (cacheKey == null || cacheKey.trim().isEmpty()) {
            return null;
        }

        String key = POPULAR_PRODUCTS_PREFIX + cacheKey;
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached instanceof List) {
            List<?> rawList = (List<?>) cached;
            List<PopularProductInfo> result = new ArrayList<>();

            for (Object item : rawList) {
                if (item instanceof PopularProductInfo) {
                    result.add((PopularProductInfo) item);
                } else if (item instanceof Map) {
                    // GenericJackson2JsonRedisSerializer가 LinkedHashMap으로 역직렬화한 경우
                    Map<?, ?> map = (Map<?, ?>) item;
                    try {
                        Long productId = ((Number) map.get("productId")).longValue();
                        Integer salesCount = ((Number) map.get("salesCount")).intValue();
                        Integer rank = ((Number) map.get("rank")).intValue();
                        LocalDate startDate = parseLocalDate(map.get("startDate"));
                        LocalDate endDate = parseLocalDate(map.get("endDate"));

                        result.add(new PopularProductInfo(productId, salesCount, rank, startDate, endDate));
                    } catch (Exception e) {
                        System.err.println("Failed to convert cached item to PopularProductInfo: " + e.getMessage());
                    }
                }
            }

            return result.isEmpty() ? null : result;
        }

        return null;
    }

    /**
     * Map에서 LocalDate 파싱 헬퍼 메서드
     */
    private LocalDate parseLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        } else if (value instanceof String) {
            return LocalDate.parse((String) value);
        } else if (value instanceof List) {
            // [year, month, day] 형식
            List<?> list = (List<?>) value;
            if (list.size() >= 3) {
                int year = ((Number) list.get(0)).intValue();
                int month = ((Number) list.get(1)).intValue();
                int day = ((Number) list.get(2)).intValue();
                return LocalDate.of(year, month, day);
            }
        }
        throw new IllegalArgumentException("Cannot parse LocalDate from: " + value);
    }

    /**
     * 인기 상품 목록을 캐시에 저장
     * 
     * @param cacheKey        캐시 키
     * @param popularProducts 인기 상품 목록
     */
    public void cachePopularProducts(String cacheKey, List<PopularProductInfo> popularProducts) {
        if (cacheKey == null || cacheKey.trim().isEmpty() || popularProducts == null) {
            return;
        }

        String key = POPULAR_PRODUCTS_PREFIX + cacheKey;
        redisTemplate.opsForValue().set(key, popularProducts, CACHE_TTL);
    }

    /**
     * 인기 상품 캐시 무효화
     */
    public void invalidatePopularProductsCache() {
        Set<String> keys = redisTemplate.keys(POPULAR_PRODUCTS_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 오래된 날짜의 데이터 정리 (메모리 관리)
     * 
     * @param cutoffDate 이 날짜 이전의 데이터를 삭제
     */
    public void cleanup(LocalDate cutoffDate) {
        if (cutoffDate == null) {
            return;
        }

        // 컷오프 날짜 이전의 데이터 제거
        // Redis는 TTL로 자동 관리되지만, 명시적으로 삭제도 가능
        Set<String> allKeys = redisTemplate.keys(DAILY_SALES_PREFIX + "*");
        if (allKeys != null) {
            List<String> keysToDelete = allKeys.stream()
                    .filter(key -> {
                        try {
                            String dateStr = key.substring(DAILY_SALES_PREFIX.length());
                            LocalDate date = LocalDate.parse(dateStr);
                            return date.isBefore(cutoffDate);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        }

        // 캐시도 무효화
        invalidatePopularProductsCache();
    }

    /**
     * 현재 캐시 통계 조회 (디버깅/모니터링용)
     * 
     * @return 캐시 통계 정보
     */
    public CacheStats getCacheStats() {
        // Redis 기반 통계 수집
        Set<String> salesKeys = redisTemplate.keys(DAILY_SALES_PREFIX + "*");
        Set<String> cacheKeys = redisTemplate.keys(POPULAR_PRODUCTS_PREFIX + "*");

        int totalDays = (salesKeys != null) ? salesKeys.size() : 0;
        int totalCachedQueries = (cacheKeys != null) ? cacheKeys.size() : 0;

        // 전체 상품 수 계산
        long totalProducts = 0;
        if (salesKeys != null) {
            for (String key : salesKeys) {
                Long size = redisTemplate.opsForHash().size(key);
                if (size != null) {
                    totalProducts += size;
                }
            }
        }

        return new CacheStats(
                0L, // Redis는 hit count를 직접 제공하지 않음
                0L, // Redis는 miss count를 직접 제공하지 않음
                0.0, // hit rate 계산 불가
                totalDays,
                (int) totalProducts,
                totalCachedQueries);
    }

    /**
     * 인기 상품 캐시 키 생성 유틸리티
     * 
     * @param days  조회 기간 (일)
     * @param limit 조회 개수
     * @return 캐시 키
     */
    public static String createCacheKey(int days, int limit) {
        return String.format("popular_%ddays_%ditems", days, limit);
    }

    /**
     * 인기 상품 정보를 담는 클래스
     */
    public static class PopularProductInfo {
        private final Long productId;
        private final int salesCount;
        private final int rank;
        private final LocalDate startDate;
        private final LocalDate endDate;

        @JsonCreator
        public PopularProductInfo(
                @JsonProperty("productId") Long productId,
                @JsonProperty("salesCount") int salesCount,
                @JsonProperty("rank") int rank,
                @JsonProperty("startDate") LocalDate startDate,
                @JsonProperty("endDate") LocalDate endDate) {
            this.productId = productId;
            this.salesCount = salesCount;
            this.rank = rank;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public Long getProductId() {
            return productId;
        }

        public int getSalesCount() {
            return salesCount;
        }

        public int getRank() {
            return rank;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }
    }

    /**
     * 캐시 통계 정보를 담는 클래스
     */
    public static class CacheStats {
        private final long hitCount;
        private final long missCount;
        private final double hitRate;
        private final int totalDaysTracked;
        private final int totalProductsTracked;
        private final int totalCachedQueries;

        public CacheStats(long hitCount, long missCount, double hitRate,
                int totalDaysTracked, int totalProductsTracked, int totalCachedQueries) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
            this.totalDaysTracked = totalDaysTracked;
            this.totalProductsTracked = totalProductsTracked;
            this.totalCachedQueries = totalCachedQueries;
        }

        public long getHitCount() {
            return hitCount;
        }

        public long getMissCount() {
            return missCount;
        }

        public double getHitRate() {
            return hitRate;
        }

        public int getTotalDaysTracked() {
            return totalDaysTracked;
        }

        public int getTotalProductsTracked() {
            return totalProductsTracked;
        }

        public int getTotalCachedQueries() {
            return totalCachedQueries;
        }
    }
}