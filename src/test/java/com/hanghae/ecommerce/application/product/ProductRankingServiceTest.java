package com.hanghae.ecommerce.application.product;

import com.hanghae.ecommerce.application.product.ProductRankingService.RankedProduct;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상품 주문 랭킹 서비스 테스트
 * 
 * Redis Sorted Set을 활용한 랭킹 기능을 검증합니다.
 */
@DisplayName("ProductRankingService 테스트")
class ProductRankingServiceTest extends BaseIntegrationTest {

  @Autowired
  private ProductRankingService productRankingService;

  @Autowired
  private ProductRepository productRepository;

  @Autowired
  private StockRepository stockRepository;

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  private List<Product> testProducts;

  @BeforeEach
  void setUp() {
    // Redis 연결이 준비될 때까지 대기
    waitForRedisConnection();

    // Redis 랭킹 초기화
    productRankingService.clearRanking();

    // 테스트 상품 생성
    testProducts = createTestProducts(5);
  }

  @AfterEach
  void tearDown() {
    // Redis 랭킹 초기화
    try {
      productRankingService.clearRanking();
    } catch (Exception e) {
      // 연결이 이미 끊어진 경우 무시
    }
  }

  /**
   * Redis 연결이 준비될 때까지 대기
   * 
   * 컨테이너 재시작 등으로 인한 연결 지연을 처리합니다.
   */
  private void waitForRedisConnection() {
    int maxRetries = 10;
    int retryCount = 0;

    while (retryCount < maxRetries) {
      try {
        // 간단한 Redis 명령으로 연결 확인
        redisTemplate.opsForValue().get("connection:test");
        return; // 연결 성공
      } catch (Exception e) {
        retryCount++;
        if (retryCount >= maxRetries) {
          throw new RuntimeException("Redis 연결 대기 실패: " + e.getMessage(), e);
        }
        try {
          Thread.sleep(100); // 100ms 대기 후 재시도
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Redis 연결 대기 중 인터럽트", ie);
        }
      }
    }
  }

  private List<Product> createTestProducts(int count) {
    List<Product> products = new java.util.ArrayList<>();
    long timestamp = System.currentTimeMillis();

    for (int i = 0; i < count; i++) {
      Product product = Product.create(
          "랭킹테스트상품_" + timestamp + "_" + i,
          "랭킹 테스트용 상품",
          Money.of(10000 + i * 1000),
          Quantity.of(10));
      product = productRepository.save(product);

      Stock stock = Stock.createForProduct(product.getId(), Quantity.of(100), null);
      stockRepository.save(stock);

      products.add(product);
    }

    return products;
  }

  @Test
  @DisplayName("단일 상품 주문 수량 증가")
  void testIncrementOrderCount() {
    // given
    Product product = testProducts.get(0);
    int quantity = 5;

    // when
    productRankingService.incrementOrderCount(product.getId(), quantity);

    // then
    Long orderCount = productRankingService.getOrderCount(product.getId());
    assertThat(orderCount).isEqualTo(5L);
  }

  @Test
  @DisplayName("여러 상품 주문 수량 증가")
  void testIncrementOrderCounts() {
    // given
    Map<Long, Integer> productOrderCounts = new HashMap<>();
    productOrderCounts.put(testProducts.get(0).getId(), 10);
    productOrderCounts.put(testProducts.get(1).getId(), 20);
    productOrderCounts.put(testProducts.get(2).getId(), 15);

    // when
    productRankingService.incrementOrderCounts(productOrderCounts);

    // then
    assertThat(productRankingService.getOrderCount(testProducts.get(0).getId())).isEqualTo(10L);
    assertThat(productRankingService.getOrderCount(testProducts.get(1).getId())).isEqualTo(20L);
    assertThat(productRankingService.getOrderCount(testProducts.get(2).getId())).isEqualTo(15L);
  }

  @Test
  @DisplayName("주문 수량 누적 증가")
  void testIncrementOrderCountAccumulation() {
    // given
    Product product = testProducts.get(0);

    // when - 여러 번 증가
    productRankingService.incrementOrderCount(product.getId(), 5);
    productRankingService.incrementOrderCount(product.getId(), 10);
    productRankingService.incrementOrderCount(product.getId(), 3);

    // then
    Long orderCount = productRankingService.getOrderCount(product.getId());
    assertThat(orderCount).isEqualTo(18L); // 5 + 10 + 3
  }

  @Test
  @DisplayName("상위 N개 랭킹 조회")
  void testGetTopRankedProducts() {
    // given
    // 상품별 주문 수량 설정
    productRankingService.incrementOrderCount(testProducts.get(0).getId(), 50); // 3위
    productRankingService.incrementOrderCount(testProducts.get(1).getId(), 100); // 1위
    productRankingService.incrementOrderCount(testProducts.get(2).getId(), 30); // 4위
    productRankingService.incrementOrderCount(testProducts.get(3).getId(), 80); // 2위
    productRankingService.incrementOrderCount(testProducts.get(4).getId(), 10); // 5위

    // when
    List<RankedProduct> topProducts = productRankingService.getTopRankedProducts(3);

    // then
    assertThat(topProducts).hasSize(3);
    assertThat(topProducts.get(0).getRank()).isEqualTo(1);
    assertThat(topProducts.get(0).getOrderCount()).isEqualTo(100L); // 가장 많이 주문된 상품
    assertThat(topProducts.get(0).getProduct().getId()).isEqualTo(testProducts.get(1).getId());

    assertThat(topProducts.get(1).getRank()).isEqualTo(2);
    assertThat(topProducts.get(1).getOrderCount()).isEqualTo(80L);
    assertThat(topProducts.get(1).getProduct().getId()).isEqualTo(testProducts.get(3).getId());

    assertThat(topProducts.get(2).getRank()).isEqualTo(3);
    assertThat(topProducts.get(2).getOrderCount()).isEqualTo(50L);
    assertThat(topProducts.get(2).getProduct().getId()).isEqualTo(testProducts.get(0).getId());
  }

  @Test
  @DisplayName("랭킹 조회 - 주문 수량이 같을 때")
  void testGetTopRankedProductsWithSameOrderCount() {
    // given
    productRankingService.incrementOrderCount(testProducts.get(0).getId(), 50);
    productRankingService.incrementOrderCount(testProducts.get(1).getId(), 50);
    productRankingService.incrementOrderCount(testProducts.get(2).getId(), 30);

    // when
    List<RankedProduct> topProducts = productRankingService.getTopRankedProducts(3);

    // then
    assertThat(topProducts).hasSize(3);
    // 주문 수량이 같으면 Redis의 정렬 순서에 따라 결정됨 (일반적으로 ID 순서)
    assertThat(topProducts.get(0).getOrderCount()).isEqualTo(50L);
    assertThat(topProducts.get(1).getOrderCount()).isEqualTo(50L);
    assertThat(topProducts.get(2).getOrderCount()).isEqualTo(30L);
  }

  @Test
  @DisplayName("특정 상품의 순위 조회")
  void testGetRank() {
    // given
    productRankingService.incrementOrderCount(testProducts.get(0).getId(), 50);
    productRankingService.incrementOrderCount(testProducts.get(1).getId(), 100);
    productRankingService.incrementOrderCount(testProducts.get(2).getId(), 30);

    // when
    int rank1 = productRankingService.getRank(testProducts.get(1).getId()); // 100개 주문
    int rank2 = productRankingService.getRank(testProducts.get(0).getId()); // 50개 주문
    int rank3 = productRankingService.getRank(testProducts.get(2).getId()); // 30개 주문

    // then
    assertThat(rank1).isEqualTo(1); // 1위
    assertThat(rank2).isEqualTo(2); // 2위
    assertThat(rank3).isEqualTo(3); // 3위
  }

  @Test
  @DisplayName("존재하지 않는 상품의 순위 조회")
  void testGetRankForNonExistentProduct() {
    // given
    Long nonExistentProductId = 99999L;

    // when
    int rank = productRankingService.getRank(nonExistentProductId);

    // then
    assertThat(rank).isEqualTo(-1);
  }

  @Test
  @DisplayName("특정 상품의 주문 수량 조회")
  void testGetOrderCount() {
    // given
    Product product = testProducts.get(0);
    productRankingService.incrementOrderCount(product.getId(), 25);

    // when
    Long orderCount = productRankingService.getOrderCount(product.getId());

    // then
    assertThat(orderCount).isEqualTo(25L);
  }

  @Test
  @DisplayName("존재하지 않는 상품의 주문 수량 조회")
  void testGetOrderCountForNonExistentProduct() {
    // given
    Long nonExistentProductId = 99999L;

    // when
    Long orderCount = productRankingService.getOrderCount(nonExistentProductId);

    // then
    assertThat(orderCount).isEqualTo(0L);
  }

  @Test
  @DisplayName("랭킹 초기화")
  void testClearRanking() {
    // given
    productRankingService.incrementOrderCount(testProducts.get(0).getId(), 10);
    productRankingService.incrementOrderCount(testProducts.get(1).getId(), 20);
    assertThat(productRankingService.getOrderCount(testProducts.get(0).getId())).isEqualTo(10L);

    // when
    productRankingService.clearRanking();

    // then
    assertThat(productRankingService.getOrderCount(testProducts.get(0).getId())).isEqualTo(0L);
    assertThat(productRankingService.getOrderCount(testProducts.get(1).getId())).isEqualTo(0L);
    List<RankedProduct> topProducts = productRankingService.getTopRankedProducts(10);
    assertThat(topProducts).isEmpty();
  }

  @Test
  @DisplayName("랭킹 조회 - 빈 랭킹")
  void testGetTopRankedProductsWhenEmpty() {
    // when
    List<RankedProduct> topProducts = productRankingService.getTopRankedProducts(10);

    // then
    assertThat(topProducts).isEmpty();
  }

  @Test
  @DisplayName("랭킹 조회 - 잘못된 limit 값")
  void testGetTopRankedProductsWithInvalidLimit() {
    // when & then
    assertThatThrownBy(() -> productRankingService.getTopRankedProducts(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("조회 개수는 1 이상이어야 합니다");

    assertThatThrownBy(() -> productRankingService.getTopRankedProducts(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("조회 개수는 1 이상이어야 합니다");
  }

  @Test
  @DisplayName("유효하지 않은 데이터 무시 - null productId")
  void testIncrementOrderCountWithNullProductId() {
    // when
    productRankingService.incrementOrderCount(null, 10);

    // then - 예외가 발생하지 않고 무시됨
    // 랭킹에 추가되지 않아야 함
    List<RankedProduct> topProducts = productRankingService.getTopRankedProducts(10);
    assertThat(topProducts).isEmpty();
  }

  @Test
  @DisplayName("유효하지 않은 데이터 무시 - 음수 수량")
  void testIncrementOrderCountWithNegativeQuantity() {
    // given
    Product product = testProducts.get(0);

    // when
    productRankingService.incrementOrderCount(product.getId(), -5);
    productRankingService.incrementOrderCount(product.getId(), 0);

    // then - 예외가 발생하지 않고 무시됨
    Long orderCount = productRankingService.getOrderCount(product.getId());
    assertThat(orderCount).isEqualTo(0L);
  }

  @Test
  @DisplayName("동시성 테스트 - 여러 주문이 동시에 들어올 때")
  void testConcurrentIncrementOrderCount() throws InterruptedException {
    // given
    Product product = testProducts.get(0);
    int concurrentRequests = 50;
    int quantityPerRequest = 2;
    int expectedTotal = concurrentRequests * quantityPerRequest;

    ExecutorService executor = Executors.newFixedThreadPool(20);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(concurrentRequests);
    AtomicInteger successCount = new AtomicInteger(0);

    // when - 50개 요청이 동시에 시작
    for (int i = 0; i < concurrentRequests; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();
          productRankingService.incrementOrderCount(product.getId(), quantityPerRequest);
          successCount.incrementAndGet();
        } catch (Exception e) {
          System.err.println("요청 실패: " + e.getMessage());
        } finally {
          endLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    boolean completed = endLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then
    assertThat(completed).isTrue();
    assertThat(successCount.get()).isEqualTo(concurrentRequests);

    // ZINCRBY는 원자적 연산이므로 정확한 값이 나와야 함
    Long finalOrderCount = productRankingService.getOrderCount(product.getId());
    assertThat(finalOrderCount)
        .as("동시 요청 시에도 정확한 주문 수량이 유지되어야 함")
        .isEqualTo((long) expectedTotal);

    System.out.println("=== 동시성 테스트 결과 ===");
    System.out.printf("동시 요청 수: %d%n", concurrentRequests);
    System.out.printf("요청당 수량: %d%n", quantityPerRequest);
    System.out.printf("기대 총 주문 수량: %d%n", expectedTotal);
    System.out.printf("실제 총 주문 수량: %d%n", finalOrderCount);
  }

  @Test
  @DisplayName("동시성 테스트 - 여러 상품에 동시 주문")
  void testConcurrentIncrementOrderCounts() throws InterruptedException {
    // given
    int concurrentRequests = 30;
    int productsPerRequest = 3;

    ExecutorService executor = Executors.newFixedThreadPool(15);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(concurrentRequests);
    AtomicInteger successCount = new AtomicInteger(0);

    // when - 여러 상품에 동시에 주문
    for (int i = 0; i < concurrentRequests; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();
          Map<Long, Integer> orderCounts = new HashMap<>();
          for (int j = 0; j < productsPerRequest && j < testProducts.size(); j++) {
            orderCounts.put(testProducts.get(j).getId(), 1);
          }
          productRankingService.incrementOrderCounts(orderCounts);
          successCount.incrementAndGet();
        } catch (Exception e) {
          System.err.println("요청 실패: " + e.getMessage());
        } finally {
          endLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    boolean completed = endLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then
    assertThat(completed).isTrue();
    assertThat(successCount.get()).isEqualTo(concurrentRequests);

    // 각 상품의 주문 수량 확인
    for (int j = 0; j < productsPerRequest && j < testProducts.size(); j++) {
      Long orderCount = productRankingService.getOrderCount(testProducts.get(j).getId());
      assertThat(orderCount)
          .as("상품 %d의 주문 수량", testProducts.get(j).getId())
          .isEqualTo((long) concurrentRequests);
    }
  }

  @Test
  @DisplayName("랭킹 조회 시 상품 정보 포함 확인")
  void testGetTopRankedProductsIncludesProductInfo() {
    // given
    Product product = testProducts.get(0);
    productRankingService.incrementOrderCount(product.getId(), 100);

    // when
    List<RankedProduct> topProducts = productRankingService.getTopRankedProducts(1);

    // then
    assertThat(topProducts).hasSize(1);
    RankedProduct rankedProduct = topProducts.get(0);
    assertThat(rankedProduct.getProduct()).isNotNull();
    assertThat(rankedProduct.getProduct().getId()).isEqualTo(product.getId());
    assertThat(rankedProduct.getProduct().getName()).isEqualTo(product.getName());
    assertThat(rankedProduct.getStock()).isNotNull();
    assertThat(rankedProduct.getOrderCount()).isEqualTo(100L);
    assertThat(rankedProduct.getRank()).isEqualTo(1);
  }
}
