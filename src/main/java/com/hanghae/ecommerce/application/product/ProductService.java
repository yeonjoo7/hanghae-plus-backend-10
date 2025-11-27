package com.hanghae.ecommerce.application.product;

import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.infrastructure.cache.RedisCacheService;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품 관리 서비스
 * 상품 조회, 인기 상품 조회 등의 비즈니스 로직을 처리합니다.
 * 
 * ## Redis 캐싱 전략
 * - 상품 상세 조회: Cache Aside 패턴 (TTL 30분)
 * - 상품+재고 조회: Cache Aside 패턴 (TTL 5분, 재고 변동이 잦음)
 * - Cache Stampede 방지: 분산락 기반 캐시 갱신
 * 
 * ## 캐시 무효화
 * - 상품 정보 수정 시 관련 캐시 무효화
 * - 재고 변경 시 productWithStock 캐시 무효화
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final OrderItemRepository orderItemRepository;
    private final RedisCacheService redisCacheService;

    // 캐시 설정
    private static final String PRODUCT_CACHE_PREFIX = "product:";
    private static final String PRODUCT_WITH_STOCK_CACHE_PREFIX = "product-with-stock:";
    private static final Duration PRODUCT_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration PRODUCT_WITH_STOCK_CACHE_TTL = Duration.ofMinutes(5);

    public ProductService(ProductRepository productRepository,
            StockRepository stockRepository,
            OrderItemRepository orderItemRepository,
            RedisCacheService redisCacheService) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.orderItemRepository = orderItemRepository;
        this.redisCacheService = redisCacheService;
    }

    /**
     * 상품 상세 조회 (Redis 캐싱 적용)
     * 
     * 캐시 전략:
     * - TTL: 30분 (상품 정보는 자주 변경되지 않음)
     * - Cache Stampede 방지: 분산락 사용
     * 
     * @param productId 상품 ID
     * @return 상품 정보
     * @throws IllegalArgumentException 상품을 찾을 수 없는 경우
     */
    public Product getProduct(Long productId) {
        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        
        ProductCacheData cachedData = redisCacheService.getOrLoad(
            cacheKey,
            ProductCacheData.class,
            PRODUCT_CACHE_TTL,
            () -> {
                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));
                return ProductCacheData.from(product);
            }
        );
        
        if (cachedData == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId);
        }
        
        return cachedData.toProduct();
    }
    
    /**
     * 캐시를 사용하지 않고 직접 DB에서 상품 조회
     * (캐시 갱신이나 관리용)
     */
    public Product getProductDirect(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId));
    }

    /**
     * 여러 상품을 한 번에 조회
     * 
     * @param productIds 상품 ID 목록
     * @return 상품 목록
     */
    public List<Product> getProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("상품 ID 목록은 비어있을 수 없습니다.");
        }

        List<Product> products = productRepository.findByIdIn(productIds);

        // 요청된 모든 상품이 존재하는지 확인
        List<Long> foundIds = products.stream()
                .map(Product::getId)
                .collect(Collectors.toList());

        List<Long> missingIds = productIds.stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 상품이 있습니다. IDs: " + missingIds);
        }

        return products;
    }

    /**
     * 상품과 재고 정보 함께 조회 (Redis 캐싱 적용)
     * 
     * 캐시 전략:
     * - TTL: 5분 (재고는 자주 변경됨)
     * - Cache Stampede 방지: 분산락 사용
     * - 재고 변경 시 캐시 무효화 필요
     * 
     * @param productId 상품 ID
     * @return 상품과 재고 정보의 쌍 (Product, Stock)
     * @throws IllegalArgumentException 상품 또는 재고를 찾을 수 없는 경우
     */
    public ProductWithStock getProductWithStock(Long productId) {
        String cacheKey = PRODUCT_WITH_STOCK_CACHE_PREFIX + productId;
        
        ProductWithStockCacheData cachedData = redisCacheService.getOrLoad(
            cacheKey,
            ProductWithStockCacheData.class,
            PRODUCT_WITH_STOCK_CACHE_TTL,
            () -> {
                Product product = getProductDirect(productId);
                Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNull(productId)
                        .orElseThrow(() -> new IllegalArgumentException("상품 재고를 찾을 수 없습니다. ProductID: " + productId));
                return ProductWithStockCacheData.from(product, stock);
            }
        );
        
        if (cachedData == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + productId);
        }
        
        return cachedData.toProductWithStock();
    }
    
    /**
     * 상품 캐시 무효화
     * 상품 정보가 수정되었을 때 호출
     * 
     * @param productId 상품 ID
     */
    public void invalidateProductCache(Long productId) {
        redisCacheService.delete(PRODUCT_CACHE_PREFIX + productId);
        redisCacheService.delete(PRODUCT_WITH_STOCK_CACHE_PREFIX + productId);
    }
    
    /**
     * 상품+재고 캐시 무효화
     * 재고가 변경되었을 때 호출 (주문, 재고 차감 등)
     * 
     * @param productId 상품 ID
     */
    public void invalidateProductWithStockCache(Long productId) {
        redisCacheService.delete(PRODUCT_WITH_STOCK_CACHE_PREFIX + productId);
    }
    
    /**
     * 여러 상품의 캐시 무효화
     * 
     * @param productIds 상품 ID 목록
     */
    public void invalidateProductCaches(List<Long> productIds) {
        for (Long productId : productIds) {
            invalidateProductCache(productId);
        }
    }

    /**
     * 여러 상품의 재고 정보 함께 조회
     * 
     * @param productIds 상품 ID 목록
     * @return 상품별 재고 정보 맵 (ProductID -> ProductWithStock)
     */
    public Map<Long, ProductWithStock> getProductsWithStock(List<Long> productIds) {
        List<Product> products = getProducts(productIds);
        List<Stock> stocks = stockRepository.findByProductIdInAndProductOptionIdIsNull(productIds);

        Map<Long, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));

        return products.stream()
                .collect(Collectors.toMap(
                        Product::getId,
                        product -> {
                            Stock stock = stockMap.get(product.getId());
                            if (stock == null) {
                                throw new IllegalArgumentException("상품 재고를 찾을 수 없습니다. ProductID: " + product.getId());
                            }
                            return new ProductWithStock(product, stock);
                        }));
    }

    /**
     * 인기 상품 조회 (최근 3일간 판매량 기준 상위 5개)
     * 
     * @return 인기 상품 목록 (판매량 순으로 정렬)
     */
    public List<PopularProduct> getPopularProducts() {
        return getPopularProducts(3, 5);
    }

    /**
     * 인기 상품 조회 (지정된 기간과 개수)
     * 
     * @param days  조회 기간 (일)
     * @param limit 조회 개수
     * @return 인기 상품 목록 (판매량 순으로 정렬)
     */
    public List<PopularProduct> getPopularProducts(int days, int limit) {
        if (days <= 0) {
            throw new IllegalArgumentException("조회 기간은 1일 이상이어야 합니다.");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("조회 개수는 1개 이상이어야 합니다.");
        }

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        // 기간 내 상품별 판매 수량 조회
        /*
         * List<OrderItemRepository.ProductSalesProjection> salesData =
         * orderItemRepository
         * .findTopSellingProducts(startDate, endDate);
         * 
         * if (salesData.isEmpty()) {
         * return List.of();
         * }
         * 
         * // limit 적용 및 ProductSalesInfo로 변환
         * List<ProductSalesInfo> salesInfoList = salesData.stream()
         * .limit(limit)
         * .map(projection -> new ProductSalesInfo(
         * projection.getProductId(),
         * projection.getTotalQuantity().intValue()))
         * .collect(Collectors.toList());
         */
        List<ProductSalesInfo> salesInfoList = List.of(); // 임시 빈 리스트

        // 상품 ID 목록 추출
        List<Long> productIds = salesInfoList.stream()
                .map(ProductSalesInfo::getProductId)
                .collect(Collectors.toList());

        Map<Long, ProductWithStock> productWithStockMap = getProductsWithStock(productIds);

        // 인기 상품 목록 생성 (판매량 순으로 정렬된 상태)
        return salesInfoList.stream()
                .map(salesInfo -> {
                    ProductWithStock productWithStock = productWithStockMap.get(salesInfo.getProductId());
                    int rank = salesInfoList.indexOf(salesInfo) + 1;
                    return new PopularProduct(
                            rank,
                            productWithStock.getProduct(),
                            productWithStock.getStock(),
                            salesInfo.getSalesCount(),
                            startDate.toLocalDate(),
                            endDate.toLocalDate());
                })
                .collect(Collectors.toList());
    }

    /**
     * 판매 가능한 상품 목록 조회
     * 
     * @return 판매 가능한 상품 목록
     */
    public List<Product> getAvailableProducts() {
        // return productRepository.findActiveProducts();
        return List.of();
    }

    /**
     * 상품 검색
     * 
     * @param keyword 검색 키워드
     * @return 검색된 상품 목록
     */
    public List<Product> searchProducts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("검색 키워드는 필수입니다.");
        }

        // return productRepository.findActiveProductsByName(keyword.trim());
        return List.of();
    }

    /**
     * 상품과 재고 정보를 담는 클래스
     */
    public static class ProductWithStock {
        private final Product product;
        private final Stock stock;

        public ProductWithStock(Product product, Stock stock) {
            this.product = product;
            this.stock = stock;
        }

        public Product getProduct() {
            return product;
        }

        public Stock getStock() {
            return stock;
        }

        public boolean isInStock() {
            return !stock.isEmpty();
        }

        public boolean hasEnoughStock(int requestQuantity) {
            return stock.hasEnoughStock(com.hanghae.ecommerce.domain.product.Quantity.of(requestQuantity));
        }
    }

    /**
     * 인기 상품 정보를 담는 클래스
     */
    public static class PopularProduct {
        private final int rank;
        private final Product product;
        private final Stock stock;
        private final int salesCount;
        private final java.time.LocalDate startDate;
        private final java.time.LocalDate endDate;

        public PopularProduct(int rank, Product product, Stock stock, int salesCount,
                java.time.LocalDate startDate, java.time.LocalDate endDate) {
            this.rank = rank;
            this.product = product;
            this.stock = stock;
            this.salesCount = salesCount;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public int getRank() {
            return rank;
        }

        public Product getProduct() {
            return product;
        }

        public Stock getStock() {
            return stock;
        }

        public int getSalesCount() {
            return salesCount;
        }

        public java.time.LocalDate getStartDate() {
            return startDate;
        }

        public java.time.LocalDate getEndDate() {
            return endDate;
        }

        public double getAverageDailySales() {
            long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            return days > 0 ? (double) salesCount / days : salesCount;
        }
    }

    /**
     * 상품 판매 정보를 담는 클래스
     */
    public static class ProductSalesInfo {
        private final Long productId;
        private final int salesCount;

        public ProductSalesInfo(Long productId, int salesCount) {
            this.productId = productId;
            this.salesCount = salesCount;
        }

        public Long getProductId() {
            return productId;
        }

        public int getSalesCount() {
            return salesCount;
        }
    }
    
    /**
     * 상품 캐시용 데이터 클래스
     * Entity를 직접 캐싱하지 않고 필요한 데이터만 추출하여 저장
     */
    public static class ProductCacheData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long id;
        private String name;
        private String description;
        private Integer price;
        private Integer limitedQuantity;
        private String state;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        public ProductCacheData() {}
        
        public static ProductCacheData from(Product product) {
            ProductCacheData data = new ProductCacheData();
            data.id = product.getId();
            data.name = product.getName();
            data.description = product.getDescription();
            data.price = product.getPrice() != null ? product.getPrice().getValue() : null;
            data.limitedQuantity = product.getLimitedQuantity() != null ? product.getLimitedQuantity().getValue() : null;
            data.state = product.getState() != null ? product.getState().name() : null;
            data.createdAt = product.getCreatedAt();
            data.updatedAt = product.getUpdatedAt();
            return data;
        }
        
        public Product toProduct() {
            return Product.restore(
                id,
                com.hanghae.ecommerce.domain.product.ProductState.valueOf(state),
                name,
                description,
                com.hanghae.ecommerce.domain.product.Money.of(price),
                limitedQuantity != null ? com.hanghae.ecommerce.domain.product.Quantity.of(limitedQuantity) : null,
                createdAt,
                updatedAt
            );
        }
        
        // Getters and Setters for JSON serialization
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getPrice() { return price; }
        public void setPrice(Integer price) { this.price = price; }
        public Integer getLimitedQuantity() { return limitedQuantity; }
        public void setLimitedQuantity(Integer limitedQuantity) { this.limitedQuantity = limitedQuantity; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
    
    /**
     * 상품+재고 캐시용 데이터 클래스
     */
    public static class ProductWithStockCacheData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private ProductCacheData product;
        private Long stockId;
        private Long stockProductId;
        private Integer availableQuantity;
        private Integer soldQuantity;
        private LocalDateTime stockCreatedAt;
        private LocalDateTime stockUpdatedAt;
        
        public ProductWithStockCacheData() {}
        
        public static ProductWithStockCacheData from(Product product, Stock stock) {
            ProductWithStockCacheData data = new ProductWithStockCacheData();
            data.product = ProductCacheData.from(product);
            data.stockId = stock.getId();
            data.stockProductId = stock.getProductId();
            data.availableQuantity = stock.getAvailableQuantity() != null ? stock.getAvailableQuantity().getValue() : 0;
            data.soldQuantity = stock.getSoldQuantity() != null ? stock.getSoldQuantity().getValue() : 0;
            data.stockCreatedAt = stock.getCreatedAt();
            data.stockUpdatedAt = stock.getUpdatedAt();
            return data;
        }
        
        public ProductWithStock toProductWithStock() {
            Product restoredProduct = product.toProduct();
            Stock restoredStock = Stock.restore(
                stockId,
                stockProductId,
                null, // productOptionId
                com.hanghae.ecommerce.domain.product.Quantity.of(availableQuantity),
                com.hanghae.ecommerce.domain.product.Quantity.of(soldQuantity),
                null, // memo
                stockCreatedAt,
                stockUpdatedAt
            );
            return new ProductWithStock(restoredProduct, restoredStock);
        }
        
        // Getters and Setters
        public ProductCacheData getProduct() { return product; }
        public void setProduct(ProductCacheData product) { this.product = product; }
        public Long getStockId() { return stockId; }
        public void setStockId(Long stockId) { this.stockId = stockId; }
        public Long getStockProductId() { return stockProductId; }
        public void setStockProductId(Long stockProductId) { this.stockProductId = stockProductId; }
        public Integer getAvailableQuantity() { return availableQuantity; }
        public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
        public Integer getSoldQuantity() { return soldQuantity; }
        public void setSoldQuantity(Integer soldQuantity) { this.soldQuantity = soldQuantity; }
        public LocalDateTime getStockCreatedAt() { return stockCreatedAt; }
        public void setStockCreatedAt(LocalDateTime stockCreatedAt) { this.stockCreatedAt = stockCreatedAt; }
        public LocalDateTime getStockUpdatedAt() { return stockUpdatedAt; }
        public void setStockUpdatedAt(LocalDateTime stockUpdatedAt) { this.stockUpdatedAt = stockUpdatedAt; }
    }
}