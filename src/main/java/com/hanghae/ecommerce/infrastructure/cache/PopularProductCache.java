package com.hanghae.ecommerce.infrastructure.cache;

import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 인기 상품 판매량 집계 시스템
 * 
 * 실시간 판매량 카운터를 관리합니다.
 */
@Component
public class PopularProductCache {

    // 일별 상품 판매량 카운터 (Date -> ProductId -> Count)
    private final Map<LocalDate, ConcurrentHashMap<Long, AtomicInteger>> dailySalesCounters = new ConcurrentHashMap<>();

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

        // 해당 날짜의 카운터 맵 가져오기 또는 생성
        ConcurrentHashMap<Long, AtomicInteger> dailyCounters = dailySalesCounters.computeIfAbsent(
                saleDate,
                date -> new ConcurrentHashMap<>());

        // 상품별 카운터 증가
        AtomicInteger counter = dailyCounters.computeIfAbsent(productId, id -> new AtomicInteger(0));
        counter.addAndGet(quantity);
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
    }

    /**
     * 현재 판매량 통계 조회 (모니터링용)
     * 
     * @return 판매량 통계 정보
     */
    public SalesStats getSalesStats() {
        int totalDays = dailySalesCounters.size();
        long totalProducts = dailySalesCounters.values().stream()
                .mapToLong(Map::size)
                .sum();
        long totalSales = dailySalesCounters.values().stream()
                .flatMap(map -> map.values().stream())
                .mapToLong(AtomicInteger::get)
                .sum();

        return new SalesStats(totalDays, (int) totalProducts, totalSales);
    }

    /**
     * 인기 상품 정보를 담는 클래스
     * Redis 캐싱에서도 사용됩니다.
     */
    public static class PopularProductInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long productId;
        private int salesCount;
        private int rank;
        private LocalDate startDate;
        private LocalDate endDate;

        public PopularProductInfo() {
            // JSON 역직렬화를 위한 기본 생성자
        }

        public PopularProductInfo(Long productId, int salesCount, int rank, LocalDate startDate, LocalDate endDate) {
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

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public void setSalesCount(int salesCount) {
            this.salesCount = salesCount;
        }

        public void setRank(int rank) {
            this.rank = rank;
        }

        public void setStartDate(LocalDate startDate) {
            this.startDate = startDate;
        }

        public void setEndDate(LocalDate endDate) {
            this.endDate = endDate;
        }
    }

    /**
     * 판매량 통계 정보를 담는 클래스
     */
    public static class SalesStats {
        private final int totalDaysTracked;
        private final int totalProductsTracked;
        private final long totalSales;

        public SalesStats(int totalDaysTracked, int totalProductsTracked, long totalSales) {
            this.totalDaysTracked = totalDaysTracked;
            this.totalProductsTracked = totalProductsTracked;
            this.totalSales = totalSales;
        }

        public int getTotalDaysTracked() {
            return totalDaysTracked;
        }

        public int getTotalProductsTracked() {
            return totalProductsTracked;
        }

        public long getTotalSales() {
            return totalSales;
        }
    }
}
