package com.hanghae.ecommerce.application.product;

import com.hanghae.ecommerce.application.product.ProductService.PopularProduct;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.infrastructure.cache.PopularProductCache;
import com.hanghae.ecommerce.infrastructure.cache.PopularProductCache.PopularProductInfo;
import com.hanghae.ecommerce.infrastructure.cache.RedisCacheService;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 인기 상품 집계 및 관리 서비스
 * 
 * 실시간 판매 데이터를 기반으로 인기 상품을 집계하고,
 * Redis 캐싱을 통해 조회 성능을 최적화합니다.
 * 
 * ## 캐싱 전략
 * - Cache Aside 패턴 적용
 * - TTL: 5분 (인기 상품은 실시간성이 중요하지만 DB 부하 감소 필요)
 * - Cache Stampede 방지: 분산락 기반 캐시 갱신
 * 
 * ## 캐시 무효화
 * - 판매 기록 시 관련 캐시 무효화 (쓰기 시점)
 * - 스케줄러를 통한 주기적 캐시 워밍업
 */
@Service
public class PopularProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final PopularProductCache cache;
    private final RedisCacheService redisCacheService;

    // Redis 캐시 설정
    private static final String REDIS_CACHE_PREFIX = "popular-products:";
    private static final Duration REDIS_CACHE_TTL = Duration.ofMinutes(5);

    public PopularProductService(ProductRepository productRepository,
                               StockRepository stockRepository,
                               PopularProductCache cache,
                               RedisCacheService redisCacheService) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.cache = cache;
        this.redisCacheService = redisCacheService;
    }

    /**
     * 판매 발생 시 호출되는 메서드 (주문 완료 시)
     * 
     * @param productId 상품 ID
     * @param quantity 판매 수량
     * @param saleDate 판매일 (null이면 오늘 날짜 사용)
     */
    public void recordSale(Long productId, int quantity, LocalDate saleDate) {
        if (productId == null || quantity <= 0) {
            return; // 유효하지 않은 데이터는 무시
        }

        LocalDate recordDate = (saleDate != null) ? saleDate : LocalDate.now();
        cache.incrementSales(productId, quantity, recordDate);
        
        // 판매 기록 시 Redis 캐시 무효화 (캐시 일관성 유지)
        invalidateRedisCache();
    }
    
    /**
     * Redis 캐시 무효화
     * 인기 상품 관련 모든 캐시를 삭제합니다.
     */
    private void invalidateRedisCache() {
        try {
            redisCacheService.deleteByPattern(REDIS_CACHE_PREFIX + "*");
        } catch (Exception e) {
            // 캐시 무효화 실패는 로그만 남기고 계속 진행
            System.err.println("Redis 캐시 무효화 실패: " + e.getMessage());
        }
    }

    /**
     * 인기 상품 조회 (기본: 최근 3일, 상위 5개)
     * 
     * @return 인기 상품 목록
     */
    public List<PopularProduct> getPopularProducts() {
        return getPopularProducts(3, 5);
    }

    /**
     * 인기 상품 조회 (기간 및 개수 지정)
     * 
     * Redis 캐싱 + Cache Stampede 방지 적용
     * 
     * @param days 조회 기간 (일)
     * @param limit 조회 개수
     * @return 인기 상품 목록
     */
    @SuppressWarnings("unchecked")
    public List<PopularProduct> getPopularProducts(int days, int limit) {
        if (days <= 0 || limit <= 0) {
            throw new IllegalArgumentException("조회 기간과 개수는 1 이상이어야 합니다.");
        }

        // Redis 캐시 키 생성
        String redisCacheKey = REDIS_CACHE_PREFIX + days + "days_" + limit + "items";
        
        // Redis 캐시에서 조회 (Cache Stampede 방지 포함)
        PopularProductCacheData cachedData = redisCacheService.getOrLoad(
            redisCacheKey,
            PopularProductCacheData.class,
            REDIS_CACHE_TTL,
            () -> {
                // 캐시 미스 시 실시간 집계
                LocalDate endDate = LocalDate.now();
                LocalDate startDate = endDate.minusDays(days - 1);
                
                List<PopularProductInfo> infos = calculatePopularProductInfos(startDate, endDate, limit);
                return new PopularProductCacheData(infos, startDate, endDate);
            }
        );
        
        if (cachedData == null || cachedData.getInfos() == null || cachedData.getInfos().isEmpty()) {
            return List.of();
        }
        
        // PopularProduct 객체로 변환하여 반환
        return convertToPopularProducts(cachedData.getInfos());
    }
    
    /**
     * 캐시용 인기 상품 정보 계산
     */
    private List<PopularProductInfo> calculatePopularProductInfos(LocalDate startDate, LocalDate endDate, int limit) {
        Map<Long, Integer> salesData = cache.getSalesCount(startDate, endDate);
        
        if (salesData.isEmpty()) {
            return List.of();
        }

        // 판매량 순으로 정렬하고 상위 N개 선택
        List<PopularProductInfo> topProducts = salesData.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .collect(Collectors.toList())
                .stream()
                .map(entry -> new PopularProductInfo(
                    entry.getKey(), 
                    entry.getValue(), 
                    0, // 순위는 나중에 계산
                    startDate, 
                    endDate
                ))
                .collect(Collectors.toList());

        // 순위 재계산
        for (int i = 0; i < topProducts.size(); i++) {
            PopularProductInfo info = topProducts.get(i);
            topProducts.set(i, new PopularProductInfo(
                info.getProductId(),
                info.getSalesCount(),
                i + 1,
                startDate,
                endDate
            ));
        }

        return topProducts;
    }

    /**
     * 특정 기간의 인기 상품 조회
     * 
     * @param startDate 시작일
     * @param endDate 종료일
     * @param limit 조회 개수
     * @return 인기 상품 목록
     */
    public List<PopularProduct> getPopularProducts(LocalDate startDate, LocalDate endDate, int limit) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("시작일과 종료일은 필수입니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일이 종료일보다 이후일 수 없습니다.");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("조회 개수는 1 이상이어야 합니다.");
        }

        // 사용자 정의 기간은 캐시하지 않음 (실시간 조회)
        List<PopularProductInfo> infos = calculatePopularProductInfos(startDate, endDate, limit);
        return convertToPopularProducts(infos);
    }

    /**
     * 인기 상품 통계 갱신 (스케줄러에서 호출)
     * 캐시를 미리 워밍업하여 성능을 향상시킵니다.
     */
    public void refreshPopularProductsCache() {
        try {
            // 기본 조회 조건들을 미리 캐싱
            getPopularProducts(3, 5);   // 3일, 5개
            getPopularProducts(7, 10);  // 7일, 10개
            getPopularProducts(1, 5);   // 1일, 5개
        } catch (Exception e) {
            // 캐시 갱신 실패는 로그만 남기고 계속 진행
            System.err.println("인기 상품 캐시 갱신 실패: " + e.getMessage());
        }
    }

    /**
     * 오래된 데이터 정리 (스케줄러에서 호출)
     * 메모리 사용량을 관리하기 위해 오래된 판매 데이터를 삭제합니다.
     * 
     * @param retentionDays 보관할 일수 (기본: 30일)
     */
    public void cleanupOldData(int retentionDays) {
        if (retentionDays <= 0) {
            retentionDays = 30; // 기본값
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
        cache.cleanup(cutoffDate);
    }

    /**
     * 판매량 통계 조회 (모니터링용)
     * 
     * @return 판매량 통계 정보
     */
    public PopularProductCache.SalesStats getSalesStats() {
        return cache.getSalesStats();
    }

    /**
     * PopularProductInfo 목록을 PopularProduct 목록으로 변환
     */
    private List<PopularProduct> convertToPopularProducts(List<PopularProductInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return List.of();
        }

        // 상품 ID 목록 추출
        List<Long> productIds = infos.stream()
                .map(PopularProductInfo::getProductId)
                .collect(Collectors.toList());

        // 상품 및 재고 정보 조회
        List<Product> products = productRepository.findByIdIn(productIds);
        List<Stock> stocks = stockRepository.findByProductIdInAndProductOptionIdIsNull(productIds);

        // 맵으로 변환 (빠른 조회를 위해)
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));

        // PopularProduct 객체 생성
        return infos.stream()
                .filter(info -> productMap.containsKey(info.getProductId()) && 
                              stockMap.containsKey(info.getProductId()))
                .map(info -> {
                    Product product = productMap.get(info.getProductId());
                    Stock stock = stockMap.get(info.getProductId());
                    
                    return new PopularProduct(
                        info.getRank(),
                        product,
                        stock,
                        info.getSalesCount(),
                        info.getStartDate(),
                        info.getEndDate()
                    );
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Redis 캐시용 데이터 클래스
     * Serializable을 구현하여 Redis에 저장 가능
     */
    public static class PopularProductCacheData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private List<PopularProductInfo> infos;
        private LocalDate startDate;
        private LocalDate endDate;
        
        public PopularProductCacheData() {
            // JSON 역직렬화를 위한 기본 생성자
        }
        
        public PopularProductCacheData(List<PopularProductInfo> infos, LocalDate startDate, LocalDate endDate) {
            this.infos = infos;
            this.startDate = startDate;
            this.endDate = endDate;
        }
        
        public List<PopularProductInfo> getInfos() { return infos; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        
        public void setInfos(List<PopularProductInfo> infos) { this.infos = infos; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    }
}