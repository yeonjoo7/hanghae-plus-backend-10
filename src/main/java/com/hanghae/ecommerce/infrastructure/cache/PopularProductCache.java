package com.hanghae.ecommerce.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 인기 상품 캐싱 시스템
 * 
 * Caffeine 캐시를 사용하여 인기 상품 조회 성능을 최적화하고,
 * 실시간 판매량 카운터를 관리합니다.
 */
@Component
public class PopularProductCache {

    // 일별 상품 판매량 카운터 (Date -> ProductId -> Count)
    private final Map<LocalDate, ConcurrentHashMap<Long, AtomicInteger>> dailySalesCounters = new ConcurrentHashMap<>();
    
    // 인기 상품 결과 캐시 (5분 TTL)
    private final Cache<String, List<PopularProductInfo>> popularProductsCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /**
     * 상품 판매량 증가
     * 
     * @param productId 상품 ID
     * @param quantity 판매 수량
     * @param saleDate 판매일
     */
    public void incrementSales(Long productId, int quantity, LocalDate saleDate) {
        if (productId == null || saleDate == null || quantity <= 0) {
            return; // 무효한 데이터는 무시
        }

        // 해당 날짜의 카운터 맵 가져오기 또는 생성
        ConcurrentHashMap<Long, AtomicInteger> dailyCounters = dailySalesCounters.computeIfAbsent(
            saleDate, 
            date -> new ConcurrentHashMap<>()
        );

        // 상품별 카운터 증가
        AtomicInteger counter = dailyCounters.computeIfAbsent(productId, id -> new AtomicInteger(0));
        counter.addAndGet(quantity);

        // 캐시 무효화 (새로운 판매 데이터로 인기 상품 순위 변경 가능)
        invalidatePopularProductsCache();
    }

    /**
     * 지정된 기간의 상품별 판매량 조회
     * 
     * @param startDate 시작일 (포함)
     * @param endDate 종료일 (포함)
     * @return 상품별 판매량 맵 (ProductId -> TotalSalesCount)
     */
    public Map<Long, Integer> getSalesCount(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return Map.of();
        }

        ConcurrentHashMap<Long, Integer> result = new ConcurrentHashMap<>();

        // 기간 내 모든 날짜의 판매량을 합산
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            ConcurrentHashMap<Long, AtomicInteger> dailyCounters = dailySalesCounters.get(currentDate);
            
            if (dailyCounters != null) {
                for (Map.Entry<Long, AtomicInteger> entry : dailyCounters.entrySet()) {
                    Long productId = entry.getKey();
                    int dailySales = entry.getValue().get();
                    
                    result.merge(productId, dailySales, Integer::sum);
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
    public List<PopularProductInfo> getCachedPopularProducts(String cacheKey) {
        if (cacheKey == null || cacheKey.trim().isEmpty()) {
            return null;
        }
        return popularProductsCache.getIfPresent(cacheKey);
    }

    /**
     * 인기 상품 목록을 캐시에 저장
     * 
     * @param cacheKey 캐시 키
     * @param popularProducts 인기 상품 목록
     */
    public void cachePopularProducts(String cacheKey, List<PopularProductInfo> popularProducts) {
        if (cacheKey == null || cacheKey.trim().isEmpty() || popularProducts == null) {
            return;
        }
        popularProductsCache.put(cacheKey, popularProducts);
    }

    /**
     * 인기 상품 캐시 무효화
     */
    public void invalidatePopularProductsCache() {
        popularProductsCache.invalidateAll();
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
        dailySalesCounters.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoffDate));
        
        // 캐시도 무효화
        invalidatePopularProductsCache();
    }

    /**
     * 현재 캐시 통계 조회 (디버깅/모니터링용)
     * 
     * @return 캐시 통계 정보
     */
    public CacheStats getCacheStats() {
        var stats = popularProductsCache.stats();
        int totalDays = dailySalesCounters.size();
        long totalProducts = dailySalesCounters.values().stream()
                .mapToLong(Map::size)
                .sum();

        return new CacheStats(
            stats.hitCount(),
            stats.missCount(),
            stats.hitRate(),
            totalDays,
            (int) totalProducts
        );
    }

    /**
     * 인기 상품 캐시 키 생성 유틸리티
     * 
     * @param days 조회 기간 (일)
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

        public PopularProductInfo(Long productId, int salesCount, int rank, LocalDate startDate, LocalDate endDate) {
            this.productId = productId;
            this.salesCount = salesCount;
            this.rank = rank;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public Long getProductId() { return productId; }
        public int getSalesCount() { return salesCount; }
        public int getRank() { return rank; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
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

        public CacheStats(long hitCount, long missCount, double hitRate, int totalDaysTracked, int totalProductsTracked) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
            this.totalDaysTracked = totalDaysTracked;
            this.totalProductsTracked = totalProductsTracked;
        }

        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public double getHitRate() { return hitRate; }
        public int getTotalDaysTracked() { return totalDaysTracked; }
        public int getTotalProductsTracked() { return totalProductsTracked; }
    }
}