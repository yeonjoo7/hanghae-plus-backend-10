package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.application.service.ProductService.PopularProduct;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.infrastructure.cache.PopularProductCache;
import com.hanghae.ecommerce.infrastructure.cache.PopularProductCache.PopularProductInfo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 인기 상품 집계 및 관리 서비스
 * 
 * 실시간 판매 데이터를 기반으로 인기 상품을 집계하고,
 * 캐싱을 통해 조회 성능을 최적화합니다.
 */
@Service
public class PopularProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final PopularProductCache cache;

    public PopularProductService(ProductRepository productRepository,
                               StockRepository stockRepository,
                               PopularProductCache cache) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.cache = cache;
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
     * @param days 조회 기간 (일)
     * @param limit 조회 개수
     * @return 인기 상품 목록
     */
    public List<PopularProduct> getPopularProducts(int days, int limit) {
        if (days <= 0 || limit <= 0) {
            throw new IllegalArgumentException("조회 기간과 개수는 1 이상이어야 합니다.");
        }

        // 캐시 키 생성
        String cacheKey = PopularProductCache.createCacheKey(days, limit);
        
        // 캐시에서 먼저 조회
        List<PopularProductInfo> cachedResult = cache.getCachedPopularProducts(cacheKey);
        if (cachedResult != null && !cachedResult.isEmpty()) {
            return convertToPopularProducts(cachedResult);
        }

        // 캐시 미스 시 실시간 집계
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1); // days일간 (오늘 포함)

        return calculatePopularProducts(startDate, endDate, limit, cacheKey);
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

        // 사용자 정의 기간은 캐시하지 않음
        return calculatePopularProducts(startDate, endDate, limit, null);
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
     * 캐시 통계 조회 (모니터링용)
     * 
     * @return 캐시 통계 정보
     */
    public PopularProductCache.CacheStats getCacheStats() {
        return cache.getCacheStats();
    }

    /**
     * 실제 인기 상품 계산 로직
     */
    private List<PopularProduct> calculatePopularProducts(LocalDate startDate, LocalDate endDate, 
                                                        int limit, String cacheKey) {
        // 기간 내 판매량 데이터 수집
        Map<Long, Integer> salesData = cache.getSalesCount(startDate, endDate);
        
        if (salesData.isEmpty()) {
            return List.of(); // 판매 데이터가 없으면 빈 목록 반환
        }

        // 판매량 순으로 정렬하고 상위 N개 선택
        List<PopularProductInfo> topProducts = salesData.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue())) // 내림차순
                .limit(limit)
                .map(entry -> {
                    AtomicInteger rank = new AtomicInteger(1);
                    return new PopularProductInfo(
                        entry.getKey(), 
                        entry.getValue(), 
                        rank.getAndIncrement(), 
                        startDate, 
                        endDate
                    );
                })
                .collect(Collectors.toList());

        // 순위 재계산
        for (int i = 0; i < topProducts.size(); i++) {
            PopularProductInfo info = topProducts.get(i);
            topProducts.set(i, new PopularProductInfo(
                info.getProductId(),
                info.getSalesCount(),
                i + 1, // 1부터 시작하는 순위
                startDate,
                endDate
            ));
        }

        // 캐시에 저장 (캐시 키가 있는 경우만)
        if (cacheKey != null) {
            cache.cachePopularProducts(cacheKey, topProducts);
        }

        // PopularProduct 객체로 변환하여 반환
        return convertToPopularProducts(topProducts);
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
}