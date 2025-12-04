package com.hanghae.ecommerce.application.product;

import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품 주문 랭킹 서비스
 * 
 * Redis Sorted Set을 활용하여 가장 많이 주문한 상품 랭킹을 실시간으로 관리합니다.
 * 
 * ## 구현 방식
 * - Redis Sorted Set 사용: Key = "product:ranking", Member = productId, Score =
 * 주문 수량
 * - 주문 완료 시 ZINCRBY로 상품별 주문 수량 증가
 * - 랭킹 조회 시 ZREVRANGE로 상위 N개 조회 (O(log(N)+M) 시간 복잡도)
 * 
 * ## 장점
 * - 실시간 랭킹 업데이트 (주문 완료 즉시 반영)
 * - 고성능 조회 (Sorted Set의 자동 정렬)
 * - DB 부하 없이 랭킹 조회 가능
 */
@Service
public class ProductRankingService {

  private final RedisTemplate<String, Object> redisTemplate;
  private final ProductRepository productRepository;
  private final StockRepository stockRepository;

  // Redis Sorted Set 키
  private static final String RANKING_KEY = "product:ranking";

  public ProductRankingService(
      RedisTemplate<String, Object> redisTemplate,
      ProductRepository productRepository,
      StockRepository stockRepository) {
    this.redisTemplate = redisTemplate;
    this.productRepository = productRepository;
    this.stockRepository = stockRepository;
  }

  /**
   * 주문 완료 시 상품별 주문 수량 증가
   * 
   * @param productId 상품 ID
   * @param quantity  주문 수량
   */
  public void incrementOrderCount(Long productId, int quantity) {
    if (productId == null || quantity <= 0) {
      return; // 유효하지 않은 데이터는 무시
    }

    ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
    // ZINCRBY: 상품의 주문 수량을 증가시킴 (없으면 생성)
    zSetOps.incrementScore(RANKING_KEY, String.valueOf(productId), quantity);
  }

  /**
   * 여러 상품의 주문 수량을 한 번에 증가
   * 
   * @param productOrderCounts 상품 ID별 주문 수량 맵
   */
  public void incrementOrderCounts(Map<Long, Integer> productOrderCounts) {
    if (productOrderCounts == null || productOrderCounts.isEmpty()) {
      return;
    }

    ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
    productOrderCounts.forEach((productId, quantity) -> {
      if (productId != null && quantity != null && quantity > 0) {
        zSetOps.incrementScore(RANKING_KEY, String.valueOf(productId), quantity);
      }
    });
  }

  /**
   * 상위 N개 인기 상품 조회
   * 
   * @param limit 조회할 상품 개수
   * @return 인기 상품 목록 (주문 수량 내림차순)
   */
  public List<RankedProduct> getTopRankedProducts(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("조회 개수는 1 이상이어야 합니다.");
    }

    ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
    // ZREVRANGE: 점수가 높은 순서대로 조회 (0부터 limit-1까지)
    Set<ZSetOperations.TypedTuple<Object>> rankedProducts = zSetOps.reverseRangeWithScores(
        RANKING_KEY, 0, limit - 1);

    if (rankedProducts == null || rankedProducts.isEmpty()) {
      return List.of();
    }

    // 상품 ID 목록 추출
    List<Long> productIds = rankedProducts.stream()
        .filter(tuple -> tuple != null && tuple.getValue() != null)
        .map(tuple -> {
          Object value = tuple.getValue();
          return value != null ? Long.valueOf(value.toString()) : null;
        })
        .filter(productId -> productId != null)
        .collect(Collectors.toList());

    // 상품 및 재고 정보 조회
    List<Product> products = productRepository.findByIdIn(productIds);
    List<Stock> stocks = stockRepository.findByProductIdInAndProductOptionIdIsNull(productIds);

    // 맵으로 변환 (빠른 조회를 위해)
    Map<Long, Product> productMap = products.stream()
        .collect(Collectors.toMap(Product::getId, Function.identity()));
    Map<Long, Stock> stockMap = stocks.stream()
        .collect(Collectors.toMap(Stock::getProductId, Function.identity()));

    // 랭킹 정보와 함께 RankedProduct 객체 생성 (Stream API 사용)
    int[] rankCounter = { 1 };
    return rankedProducts.stream()
        .filter(tuple -> tuple != null && tuple.getValue() != null)
        .map(tuple -> {
          Object value = tuple.getValue();
          if (value == null) {
            return null;
          }

          Long productId = Long.valueOf(value.toString());
          Double score = tuple.getScore();
          Long orderCount = score != null ? score.longValue() : 0L;

          Product product = productMap.get(productId);
          Stock stock = stockMap.get(productId);

          if (product != null && stock != null) {
            return new RankedProduct(rankCounter[0]++, product, stock, orderCount);
          }
          return null;
        })
        .filter(rankedProduct -> rankedProduct != null)
        .collect(Collectors.toList());
  }

  /**
   * 특정 상품의 순위 조회
   * 
   * @param productId 상품 ID
   * @return 순위 (1부터 시작, 없으면 -1)
   */
  public int getRank(Long productId) {
    if (productId == null) {
      return -1;
    }

    ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
    // ZREVRANK: 역순 순위 조회 (점수가 높을수록 순위가 낮음)
    Long rank = zSetOps.reverseRank(RANKING_KEY, String.valueOf(productId));

    return rank != null ? rank.intValue() + 1 : -1; // 0-based를 1-based로 변환
  }

  /**
   * 특정 상품의 주문 수량 조회
   * 
   * @param productId 상품 ID
   * @return 주문 수량 (없으면 0)
   */
  public Long getOrderCount(Long productId) {
    if (productId == null) {
      return 0L;
    }

    ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
    // ZSCORE: 상품의 점수(주문 수량) 조회
    Double score = zSetOps.score(RANKING_KEY, String.valueOf(productId));

    return score != null ? score.longValue() : 0L;
  }

  /**
   * 랭킹 초기화 (테스트용)
   */
  public void clearRanking() {
    redisTemplate.delete(RANKING_KEY);
  }

  /**
   * 랭킹된 상품 정보를 담는 클래스
   */
  public static class RankedProduct {
    private final int rank;
    private final Product product;
    private final Stock stock;
    private final Long orderCount;

    public RankedProduct(int rank, Product product, Stock stock, Long orderCount) {
      this.rank = rank;
      this.product = product;
      this.stock = stock;
      this.orderCount = orderCount;
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

    public Long getOrderCount() {
      return orderCount;
    }
  }
}
