package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 인기 상품 조회 응답 DTO
 */
@Getter
public class PopularProductResponse {
    private final List<PopularProductItem> products;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime updatedAt;

    public PopularProductResponse(List<PopularProductItem> products, LocalDateTime updatedAt) {
        this.products = products;
        this.updatedAt = updatedAt;
    }

    @Getter
    public static class PopularProductItem {
        private final Integer rank;
        private final Long productId;
        private final String name;
        private final Integer price;
        private final Integer stock;
        private final Integer salesCount;
        private final SalesPeriod salesPeriod;

        public PopularProductItem(Integer rank, Long productId, String name, Integer price, 
                                Integer stock, Integer salesCount, SalesPeriod salesPeriod) {
            this.rank = rank;
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.salesCount = salesCount;
            this.salesPeriod = salesPeriod;
        }
    }

    @Getter
    public static class SalesPeriod {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private final LocalDate startDate;
        
        @JsonFormat(pattern = "yyyy-MM-dd")
        private final LocalDate endDate;

        public SalesPeriod(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}