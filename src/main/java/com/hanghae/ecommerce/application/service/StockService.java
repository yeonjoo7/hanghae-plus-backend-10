package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 재고 관리 서비스 (동시성 제어 강화)
 * 
 * 재고 조회, 차감, 복구 등의 비즈니스 로직을 처리하며 
 * LockManager를 사용하여 강화된 동시성 제어를 제공합니다.
 * 
 * 주요 개선사항:
 * - ReentrantLock 기반 락 관리로 성능 향상
 * - 타임아웃 지원으로 데드락 방지
 * - 상품별 독립적 락으로 동시성 극대화
 */
@Service
public class StockService {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final LockManager lockManager;

    public StockService(StockRepository stockRepository, 
                       ProductRepository productRepository,
                       LockManager lockManager) {
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.lockManager = lockManager;
    }

    /**
     * 상품 재고 조회
     * 
     * @param productId 상품 ID
     * @return 재고 정보
     * @throws IllegalArgumentException 재고를 찾을 수 없는 경우
     */
    public Stock getStock(Long productId) {
        return stockRepository.findByProductIdAndProductOptionIdIsNull(productId)
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다. ProductID: " + productId));
    }

    /**
     * 여러 상품의 재고 조회
     * 
     * @param productIds 상품 ID 목록
     * @return 상품별 재고 정보 맵
     */
    public Map<Long, Stock> getStocks(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("상품 ID 목록은 비어있을 수 없습니다.");
        }

        List<Stock> stocks = stockRepository.findByProductIdInAndProductOptionIdIsNull(productIds);
        
        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));
        
        // 요청된 모든 상품의 재고가 존재하는지 확인
        List<Long> missingStockProductIds = productIds.stream()
                .filter(productId -> !stockMap.containsKey(productId))
                .collect(Collectors.toList());
        
        if (!missingStockProductIds.isEmpty()) {
            throw new IllegalArgumentException("재고를 찾을 수 없는 상품이 있습니다. ProductIDs: " + missingStockProductIds);
        }

        return stockMap;
    }

    /**
     * 재고 충분 여부 확인
     * 
     * @param productId 상품 ID
     * @param requestQuantity 요청 수량
     * @return 재고 충분 여부
     */
    public boolean hasEnoughStock(Long productId, int requestQuantity) {
        if (requestQuantity <= 0) {
            return false;
        }

        Stock stock = getStock(productId);
        return stock.hasEnoughStock(Quantity.of(requestQuantity));
    }

    /**
     * 여러 상품의 재고 충분 여부 확인
     * 
     * @param stockRequests 상품별 요청 수량 맵
     * @return 재고 충족 결과
     */
    public StockCheckResult checkStockAvailability(Map<Long, Integer> stockRequests) {
        if (stockRequests == null || stockRequests.isEmpty()) {
            throw new IllegalArgumentException("재고 확인 요청은 비어있을 수 없습니다.");
        }

        List<Long> productIds = List.copyOf(stockRequests.keySet());
        Map<Long, Stock> stocks = getStocks(productIds);
        
        List<StockShortage> shortages = stockRequests.entrySet().stream()
                .filter(entry -> {
                    Long productId = entry.getKey();
                    Integer requestQuantity = entry.getValue();
                    Stock stock = stocks.get(productId);
                    return !stock.hasEnoughStock(Quantity.of(requestQuantity));
                })
                .map(entry -> {
                    Long productId = entry.getKey();
                    Integer requestQuantity = entry.getValue();
                    Stock stock = stocks.get(productId);
                    return new StockShortage(productId, requestQuantity, stock.getAvailableQuantity().getValue());
                })
                .collect(Collectors.toList());

        return new StockCheckResult(shortages.isEmpty(), shortages);
    }

    /**
     * 재고 차감 (주문 시 사용 - LockManager 기반 동시성 제어)
     * 
     * @param productId 상품 ID
     * @param quantity 차감할 수량
     * @throws IllegalArgumentException 상품 또는 재고를 찾을 수 없거나, 재고가 부족한 경우
     * @throws RuntimeException 락 획득 실패 또는 동시성 오류
     */
    public void reduceStock(Long productId, int quantity) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 null일 수 없습니다.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감할 수량은 0보다 커야 합니다.");
        }

        String lockKey = "stock:" + productId;
        
        lockManager.executeWithLock(lockKey, () -> {
            // 상품 판매 가능 여부 확인
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));
            
            if (!product.isAvailable()) {
                throw new IllegalArgumentException("판매 중지된 상품입니다. ID: " + productId);
            }

            // 구매 제한 수량 확인
            if (product.exceedsLimitedQuantity(Quantity.of(quantity))) {
                throw new IllegalArgumentException("구매 제한 수량을 초과했습니다. 제한: " + 
                    product.getLimitedQuantity().getValue() + ", 요청: " + quantity);
            }

            // 재고 조회 및 차감
            Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNullForUpdate(productId)
                    .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다. ProductID: " + productId));
            
            // 재고 부족 확인
            if (!stock.hasEnoughStock(Quantity.of(quantity))) {
                throw new IllegalArgumentException(String.format(
                    "재고가 부족합니다. 요청: %d, 현재 재고: %d", 
                    quantity, stock.getAvailableQuantity().getValue()
                ));
            }

            // 원자적 재고 차감
            stock.reduceStock(Quantity.of(quantity));
            stockRepository.save(stock);

            // 재고 소진 시 상품 품절 처리
            if (stock.isEmpty()) {
                product.markOutOfStock();
                productRepository.save(product);
            }
            
            return null; // Void 작업이므로 null 반환
        });
    }

    /**
     * 여러 상품의 재고 일괄 차감 (주문 시 사용 - 동시성 제어)
     * 
     * @param stockReductions 상품별 차감 수량 맵
     * @throws IllegalArgumentException 재고가 부족한 상품이 있는 경우
     */
    public void reduceStocks(Map<Long, Integer> stockReductions) {
        if (stockReductions == null || stockReductions.isEmpty()) {
            throw new IllegalArgumentException("재고 차감 요청은 비어있을 수 없습니다.");
        }

        // 먼저 모든 상품의 재고 충분 여부를 확인
        StockCheckResult checkResult = checkStockAvailability(stockReductions);
        if (!checkResult.isAllStockAvailable()) {
            throw new IllegalArgumentException("재고가 부족한 상품이 있습니다: " + checkResult.getShortages());
        }

        // 상품 ID 정렬하여 데드락 방지
        List<Long> sortedProductIds = stockReductions.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        // 순차적으로 재고 차감
        for (Long productId : sortedProductIds) {
            Integer quantity = stockReductions.get(productId);
            reduceStock(productId, quantity);
        }
    }

    /**
     * 재고 복구 (주문 취소 시 사용 - LockManager 기반 동시성 제어)
     * 
     * @param productId 상품 ID
     * @param quantity 복구할 수량
     * @throws IllegalArgumentException 상품 또는 재고를 찾을 수 없는 경우
     * @throws RuntimeException 락 획득 실패 또는 동시성 오류
     */
    public void restoreStock(Long productId, int quantity) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 null일 수 없습니다.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("복구할 수량은 0보다 커야 합니다.");
        }

        String lockKey = "stock:" + productId;
        
        lockManager.executeWithLock(lockKey, () -> {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));

            Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNullForUpdate(productId)
                    .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다. ProductID: " + productId));
            
            boolean wasEmpty = stock.isEmpty();
            
            // 원자적 재고 복구
            stock.restoreStock(Quantity.of(quantity));
            stockRepository.save(stock);

            // 품절 상태에서 재고가 복구되면 판매 재개
            if (wasEmpty && !stock.isEmpty() && !product.isAvailable()) {
                product.markInStock();
                productRepository.save(product);
            }
            
            return null; // Void 작업이므로 null 반환
        });
    }

    /**
     * 여러 상품의 재고 일괄 복구 (주문 취소 시 사용)
     * 
     * @param stockRestorations 상품별 복구 수량 맵
     */
    public void restoreStocks(Map<Long, Integer> stockRestorations) {
        if (stockRestorations == null || stockRestorations.isEmpty()) {
            throw new IllegalArgumentException("재고 복구 요청은 비어있을 수 없습니다.");
        }

        // 상품 ID 정렬하여 데드락 방지
        List<Long> sortedProductIds = stockRestorations.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        // 순차적으로 재고 복구
        for (Long productId : sortedProductIds) {
            Integer quantity = stockRestorations.get(productId);
            restoreStock(productId, quantity);
        }
    }

    /**
     * 관리자용 재고 추가
     * 
     * @param productId 상품 ID
     * @param quantity 추가할 수량
     * @param memo 메모
     */
    public void addStock(Long productId, int quantity, String memo) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("추가할 수량은 0보다 커야 합니다.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));

        Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNull(productId)
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다. ProductID: " + productId));
        
        boolean wasEmpty = stock.isEmpty();
        
        stock.addStock(Quantity.of(quantity));
        if (memo != null) {
            stock.updateMemo(memo);
        }
        stockRepository.save(stock);

        // 품절 상태에서 재고가 추가되면 판매 재개
        if (wasEmpty && !stock.isEmpty() && !product.isAvailable()) {
            product.markInStock();
            productRepository.save(product);
        }
    }

    /**
     * 재고 확인 결과
     */
    public static class StockCheckResult {
        private final boolean allStockAvailable;
        private final List<StockShortage> shortages;

        public StockCheckResult(boolean allStockAvailable, List<StockShortage> shortages) {
            this.allStockAvailable = allStockAvailable;
            this.shortages = shortages;
        }

        public boolean isAllStockAvailable() {
            return allStockAvailable;
        }

        public List<StockShortage> getShortages() {
            return shortages;
        }
    }

    /**
     * 재고 부족 정보
     */
    public static class StockShortage {
        private final Long productId;
        private final int requestedQuantity;
        private final int availableQuantity;

        public StockShortage(Long productId, int requestedQuantity, int availableQuantity) {
            this.productId = productId;
            this.requestedQuantity = requestedQuantity;
            this.availableQuantity = availableQuantity;
        }

        public Long getProductId() {
            return productId;
        }

        public int getRequestedQuantity() {
            return requestedQuantity;
        }

        public int getAvailableQuantity() {
            return availableQuantity;
        }

        @Override
        public String toString() {
            return "StockShortage{" +
                    "productId=" + productId +
                    ", requested=" + requestedQuantity +
                    ", available=" + availableQuantity +
                    '}';
        }
    }
}