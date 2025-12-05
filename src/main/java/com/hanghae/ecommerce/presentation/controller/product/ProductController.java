package com.hanghae.ecommerce.presentation.controller.product;

import com.hanghae.ecommerce.application.product.ProductService;
import com.hanghae.ecommerce.application.product.ProductService.PopularProduct;
import com.hanghae.ecommerce.application.product.ProductService.ProductWithStock;
import com.hanghae.ecommerce.application.product.ProductRankingService;
import com.hanghae.ecommerce.application.product.ProductRankingService.RankedProduct;
import com.hanghae.ecommerce.common.ApiResponse;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.ProductState;
import com.hanghae.ecommerce.presentation.dto.PopularProductResponse;
import com.hanghae.ecommerce.presentation.dto.ProductDetailResponse;
import com.hanghae.ecommerce.presentation.dto.ProductListResponse;
import com.hanghae.ecommerce.presentation.dto.ProductRankingResponse;
import com.hanghae.ecommerce.presentation.dto.ProductSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 상품 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductRankingService productRankingService;

    /**
     * 상품 상세 조회
     * GET /products/{productId}
     */
    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long productId) {
        try {
            ProductWithStock productWithStock = productService.getProductWithStock(productId);
            Product product = productWithStock.getProduct();

            Integer availableQuantity = productWithStock.getStock() != null
                    ? productWithStock.getStock().getAvailableQuantity().getValue()
                    : null;

            ProductDetailResponse response = new ProductDetailResponse(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice().getValue(),
                    availableQuantity,
                    product.hasLimitedQuantity() ? product.getLimitedQuantity().getValue() : null,
                    mapProductStatus(product.getState()),
                    product.getCreatedAt(),
                    product.getUpdatedAt());

            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    /**
     * 여러 상품 조회
     * GET /products?ids={productIds}
     */
    @GetMapping
    public ApiResponse<ProductListResponse> getProducts(
            @RequestParam("ids") @NotEmpty(message = "상품 ID는 필수입니다") String ids) {

        List<Long> productIds = parseProductIds(ids);
        Map<Long, ProductWithStock> productsWithStock = productService.getProductsWithStock(productIds);

        List<ProductSummaryResponse> products = productIds.stream()
                .map(productId -> {
                    ProductWithStock productWithStock = productsWithStock.get(productId);
                    Product product = productWithStock.getProduct();

                    Integer availableQuantity = productWithStock.getStock() != null
                            ? productWithStock.getStock().getAvailableQuantity().getValue()
                            : null;

                    return new ProductSummaryResponse(
                            product.getId(),
                            product.getName(),
                            product.getPrice().getValue(),
                            availableQuantity,
                            mapProductStatus(product.getState()));
                })
                .collect(Collectors.toList());

        ProductListResponse response = new ProductListResponse(products, products.size());
        return ApiResponse.success(response);
    }

    /**
     * 인기 상품 조회
     * GET /products/popular
     */
    @GetMapping("/popular")
    public ApiResponse<PopularProductResponse> getPopularProducts() {
        List<PopularProduct> popularProducts = productService.getPopularProducts();

        List<PopularProductResponse.PopularProductItem> products = popularProducts.stream()
                .map(popularProduct -> {
                    PopularProductResponse.SalesPeriod salesPeriod = new PopularProductResponse.SalesPeriod(
                            popularProduct.getStartDate(),
                            popularProduct.getEndDate());

                    Integer availableQuantity = popularProduct.getStock() != null
                            ? popularProduct.getStock().getAvailableQuantity().getValue()
                            : null;

                    return new PopularProductResponse.PopularProductItem(
                            popularProduct.getRank(),
                            popularProduct.getProduct().getId(),
                            popularProduct.getProduct().getName(),
                            popularProduct.getProduct().getPrice().getValue(),
                            availableQuantity,
                            popularProduct.getSalesCount(),
                            salesPeriod);
                })
                .collect(Collectors.toList());

        PopularProductResponse response = new PopularProductResponse(products, LocalDateTime.now());
        return ApiResponse.success(response);
    }

    /**
     * 상품 주문 랭킹 조회 (Redis Sorted Set 기반)
     * GET /products/ranking?limit={limit}
     * 
     * @param limit 조회할 상품 개수 (기본값: 10)
     * @return 주문 수량 기준 상위 랭킹 상품 목록
     */
    @GetMapping("/ranking")
    public ApiResponse<ProductRankingResponse> getProductRanking(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("조회 개수는 1 이상 100 이하여야 합니다.");
        }

        List<RankedProduct> rankedProducts = productRankingService.getTopRankedProducts(limit);

        List<ProductRankingResponse.RankedProductItem> products = rankedProducts.stream()
                .map(rankedProduct -> {
                    Integer availableQuantity = rankedProduct.getStock() != null
                            ? rankedProduct.getStock().getAvailableQuantity().getValue()
                            : null;

                    return new ProductRankingResponse.RankedProductItem(
                            rankedProduct.getRank(),
                            rankedProduct.getProduct().getId(),
                            rankedProduct.getProduct().getName(),
                            rankedProduct.getProduct().getPrice().getValue(),
                            availableQuantity,
                            rankedProduct.getOrderCount());
                })
                .collect(Collectors.toList());

        ProductRankingResponse response = new ProductRankingResponse(products, LocalDateTime.now());
        return ApiResponse.success(response);
    }

    /**
     * 상품 ID 문자열 파싱
     */
    private List<Long> parseProductIds(String ids) {
        try {
            return Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("상품 ID는 숫자여야 합니다: " + ids);
        }
    }

    /**
     * 상품 상태를 API 응답용 문자열로 변환
     */
    private String mapProductStatus(ProductState state) {
        switch (state) {
            case NORMAL:
                return "AVAILABLE";
            case OUT_OF_STOCK:
                return "OUT_OF_STOCK";
            case DISCONTINUED:
            case DELETED:
            default:
                return "UNAVAILABLE";
        }
    }
}