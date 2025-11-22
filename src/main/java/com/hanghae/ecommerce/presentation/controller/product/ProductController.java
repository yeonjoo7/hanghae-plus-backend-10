package com.hanghae.ecommerce.presentation.controller.product;

import com.hanghae.ecommerce.common.ApiResponse;
import com.hanghae.ecommerce.presentation.dto.product.ProductListResponse;
import com.hanghae.ecommerce.presentation.dto.product.ProductResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/products")
public class ProductController {

    private final Map<Long, ProductResponse> products = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public ProductController() {
        // 초기 Mock 데이터
        createProduct("노트북", "고성능 노트북", 1500000, 50, 5);
        createProduct("마우스", "무선 마우스", 30000, 100, 10);
        createProduct("키보드", "기계식 키보드", 150000, 75, 5);
        createProduct("모니터", "27인치 모니터", 400000, 30, 3);
        createProduct("무선 이어폰", "노이즈 캔슬링", 150000, 100, 5);
    }

    private void createProduct(String name, String description, int price, int stock, int maxQuantity) {
        Long id = idGenerator.getAndIncrement();
        ProductResponse product = ProductResponse.builder()
                .productId(id)
                .name(name)
                .description(description)
                .price(price)
                .stock(stock)
                .maxQuantityPerCart(maxQuantity)
                .status(stock > 0 ? "AVAILABLE" : "OUT_OF_STOCK")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        products.put(id, product);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable Long productId) {
        ProductResponse product = products.get(productId);
        if (product == null) {
            return ApiResponse.error("PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다");
        }
        return ApiResponse.success(product);
    }

    @GetMapping
    public ApiResponse<ProductListResponse> getProducts(@RequestParam String ids) {
        String[] idArray = ids.split(",");
        List<ProductListResponse.ProductSummary> productList = new ArrayList<>();

        for (String idStr : idArray) {
            try {
                Long id = Long.parseLong(idStr.trim());
                ProductResponse product = products.get(id);
                if (product != null) {
                    productList.add(ProductListResponse.ProductSummary.builder()
                            .productId(product.getProductId())
                            .name(product.getName())
                            .price(product.getPrice())
                            .stock(product.getStock())
                            .status(product.getStatus())
                            .build());
                }
            } catch (NumberFormatException e) {
                // Skip invalid IDs
            }
        }

        ProductListResponse response = ProductListResponse.builder()
                .products(productList)
                .totalCount(productList.size())
                .build();

        return ApiResponse.success(response);
    }

    @GetMapping("/popular")
    public ApiResponse<Map<String, Object>> getPopularProducts() {
        // 상위 5개 상품 반환
        List<Map<String, Object>> popularProducts = products.values().stream()
                .limit(5)
                .map(p -> {
                    Map<String, Object> product = new HashMap<>();
                    product.put("rank", products.values().stream()
                            .collect(Collectors.toList()).indexOf(p) + 1);
                    product.put("productId", p.getProductId());
                    product.put("name", p.getName());
                    product.put("price", p.getPrice());
                    product.put("stock", p.getStock());
                    product.put("salesCount", 100 - (p.getProductId().intValue() * 10));

                    Map<String, String> period = new HashMap<>();
                    period.put("startDate", LocalDateTime.now().minusDays(3).toLocalDate().toString());
                    period.put("endDate", LocalDateTime.now().toLocalDate().toString());
                    product.put("salesPeriod", period);

                    return product;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("products", popularProducts);
        response.put("updatedAt", LocalDateTime.now());

        return ApiResponse.success(response);
    }
}
