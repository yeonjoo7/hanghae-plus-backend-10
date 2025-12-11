package com.hanghae.ecommerce.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 주문 랭킹 조회 응답 DTO
 */
@Getter
public class ProductRankingResponse {
  private final List<RankedProductItem> products;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private final LocalDateTime updatedAt;

  public ProductRankingResponse(List<RankedProductItem> products, LocalDateTime updatedAt) {
    this.products = products;
    this.updatedAt = updatedAt;
  }

  @Getter
  public static class RankedProductItem {
    private final Integer rank;
    private final Long productId;
    private final String name;
    private final Integer price;
    private final Integer stock;
    private final Long orderCount;

    public RankedProductItem(Integer rank, Long productId, String name, Integer price,
        Integer stock, Long orderCount) {
      this.rank = rank;
      this.productId = productId;
      this.name = name;
      this.price = price;
      this.stock = stock;
      this.orderCount = orderCount;
    }
  }
}
