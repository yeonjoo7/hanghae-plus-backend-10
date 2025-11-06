package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품 관리 서비스
 * 상품 조회, 인기 상품 조회 등의 비즈니스 로직을 처리합니다.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductService(ProductRepository productRepository, 
                         StockRepository stockRepository,
                         OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * 상품 상세 조회
     * 
     * @param productId 상품 ID
     * @return 상품 정보
     * @throws IllegalArgumentException 상품을 찾을 수 없는 경우
     */
    public Product getProduct(Long productId) {
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
     * 상품과 재고 정보 함께 조회
     * 
     * @param productId 상품 ID
     * @return 상품과 재고 정보의 쌍 (Product, Stock)
     * @throws IllegalArgumentException 상품 또는 재고를 찾을 수 없는 경우
     */
    public ProductWithStock getProductWithStock(Long productId) {
        Product product = getProduct(productId);
        Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNull(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품 재고를 찾을 수 없습니다. ProductID: " + productId));
        
        return new ProductWithStock(product, stock);
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
                    }
                ));
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
     * @param days 조회 기간 (일)
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
        List<OrderItemRepository.ProductSalesInfo> repositorySalesInfoList = orderItemRepository.findTopSellingProducts(
                startDate, endDate, limit);
        
        List<ProductSalesInfo> salesInfoList = repositorySalesInfoList.stream()
            .map(repoInfo -> new ProductSalesInfo(repoInfo.getProductId(), repoInfo.getTotalQuantity().intValue()))
            .collect(Collectors.toList());

        if (salesInfoList.isEmpty()) {
            return List.of();
        }

        // 상품 정보 조회
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
                            endDate.toLocalDate()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 판매 가능한 상품 목록 조회
     * 
     * @return 판매 가능한 상품 목록
     */
    public List<Product> getAvailableProducts() {
        return productRepository.findByStateIsAvailable();
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

        return productRepository.findByNameContainingAndStateIsAvailable(keyword.trim());
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
}