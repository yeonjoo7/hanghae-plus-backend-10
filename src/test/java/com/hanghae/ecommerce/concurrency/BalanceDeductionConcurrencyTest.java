package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 잔액 차감 동시성 테스트
 * MySQL 환경에서 실제 API 엔드포인트를 사용하여 포인트 잔액 차감의 동시성 제어를 검증합니다.
 */
@DisplayName("잔액 차감 동시성 테스트")
class BalanceDeductionConcurrencyTest extends BaseConcurrencyTest {

  @Autowired
  private com.hanghae.ecommerce.domain.payment.repository.PaymentRepository paymentRepository;

  @Autowired
  private com.hanghae.ecommerce.domain.order.repository.OrderRepository orderRepository;

  @Autowired
  private com.hanghae.ecommerce.domain.cart.repository.CartItemRepository cartItemRepository;

  @Autowired
  private com.hanghae.ecommerce.domain.cart.repository.CartRepository cartRepository;

  /**
   * 각 테스트 후 데이터 정리
   */
  @Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  @AfterEach
  @Transactional
  void cleanup() {
    // 역순으로 삭제 (의존성 순서)
    paymentRepository.deleteAll();
    orderRepository.deleteAll();
    cartItemRepository.deleteAll();
    cartRepository.deleteAll();
    stockRepository.deleteAll();
    productRepository.deleteAll();
    userRepository.deleteAll();

    // Reset auto-increment
    jdbcTemplate.execute("ALTER TABLE users AUTO_INCREMENT = 1");
  }

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    // Reset auto-increment
    jdbcTemplate.execute("ALTER TABLE users AUTO_INCREMENT = 1");
  }

  @Test
  @DisplayName("동시 잔액 차감 - 5번의 주문 요청 (잔액 충분)")
  void testConcurrentBalanceDeduction() throws Exception {
    // given: 1명의 사용자 (10만원)
    System.out.println("TEST STARTED: testConcurrentBalanceDeduction");

    // Force insert User 1
    jdbcTemplate.update(
        "INSERT INTO users (id, email, name, phone, available_point, state, type, created_at, updated_at) VALUES (1, 'balance-test@example.com', '잔액테스트유저', '010-1234-5678', 1000000, 'NORMAL', 'CUSTOMER', NOW(), NOW())");

    User testUser = userRepository.findById(1L).orElseThrow();

    // 5개의 서로 다른 상품 생성
    List<Product> products = createProductsInNewTransaction(5, 10000, 10); // 1만원, 재고 10개

    // 카트 미리 생성 (동시성 문제 회피)
    createCartInNewTransaction(testUser.getId());

    ExecutorService executor = Executors.newFixedThreadPool(5);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(products.size());
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger cartSuccessCount = new AtomicInteger(0);
    AtomicInteger orderSuccessCount = new AtomicInteger(0);
    AtomicInteger paymentSuccessCount = new AtomicInteger(0);

    // when: 5개 상품을 동시에 주문 (같은 사용자)
    for (int i = 0; i < products.size(); i++) {
      final Product product = products.get(i);
      executor.submit(() -> {
        try {
          startLatch.await();

          // 1. 장바구니에 추가
          String addToCartRequest = String.format(
              "{\"productId\": %d, \"quantity\": 1}",
              product.getId());

          MvcResult cartResult = mockMvc.perform(
              post("/carts/items")
                  .header("Authorization", "Bearer " + generateToken(testUser))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(addToCartRequest))
              .andReturn();

          if (cartResult.getResponse().getStatus() == 201) {
            cartSuccessCount.incrementAndGet();
            Long cartItemId = extractCartItemId(cartResult.getResponse().getContentAsString());

            // 2. 주문 생성
            MvcResult orderResult = mockMvc.perform(
                post("/orders")
                    .header("Authorization", "Bearer " + generateToken(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createOrderRequest(cartItemId)))
                .andReturn();

            if (orderResult.getResponse().getStatus() == 201) {
              orderSuccessCount.incrementAndGet();
              Long orderId = extractOrderId(orderResult.getResponse().getContentAsString());

              // 3. 결제
              MvcResult paymentResult = mockMvc.perform(
                  post("/orders/{orderId}/payment", orderId)
                      .header("Authorization", "Bearer " + generateToken(testUser))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"paymentMethod\": \"POINT\"}"))
                  .andReturn();

              if (paymentResult.getResponse().getStatus() == 200) {
                paymentSuccessCount.incrementAndGet();
                successCount.incrementAndGet();
              } else {
                System.err.println("Payment failed with status: " + paymentResult.getResponse().getStatus() +
                    ", body: " + paymentResult.getResponse().getContentAsString());
              }
            } else {
              System.err.println("Order creation failed with status: " + orderResult.getResponse().getStatus() +
                  ", body: " + orderResult.getResponse().getContentAsString());
            }
          } else {
            System.err.println("Add to cart failed with status: " + cartResult.getResponse().getStatus() +
                ", body: " + cartResult.getResponse().getContentAsString());
          }
        } catch (Exception e) {
          System.err.println("Exception in test: " + e.getClass().getName() + ": " + e.getMessage());
          e.printStackTrace();
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // Print counters for debugging
    System.out.println("=== Test Results ===");
    System.out.println("Cart success: " + cartSuccessCount.get() + "/5");
    System.out.println("Order success: " + orderSuccessCount.get() + "/5");
    System.out.println("Payment success: " + paymentSuccessCount.get() + "/5");
    System.out.println("Total success: " + successCount.get() + "/5");

    // then: 모두 성공해야 함 (5개 상품 구매)
    assertThat(completed).isTrue();
    assertThat(cartSuccessCount.get())
        .as("Cart additions should all succeed")
        .isEqualTo(5);
    assertThat(orderSuccessCount.get())
        .as("Order creations should all succeed")
        .isEqualTo(5);
    assertThat(paymentSuccessCount.get())
        .as("Payments should all succeed")
        .isEqualTo(5);
    assertThat(successCount.get()).isEqualTo(5);

    // 사용자의 잔액 확인 (100,000 - 50,000 = 50,000)
    User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
    assertThat(updatedUser.getAvailablePoint().getValue()).isEqualTo(50000);
  }

  @Test
  @DisplayName("잔액 부족 시 일부만 성공 - 30,000원으로 5개 상품(각 10,000원) 구매 시도")
  void testConcurrentBalanceDeductionWithInsufficientBalance() throws Exception {
    // given: 1명의 사용자 (30,000원)와 5개 상품
    // NOTE: CartController가 현재 hardcoded userId=1을 사용
    final User testUser = createUserInNewTransaction("test2@example.com", "테스트유저2", 30000);
    List<Product> products = createProductsInNewTransaction(5, 10000, 100);

    // 카트 미리 생성 (동시성 문제 회피)
    createCartInNewTransaction(testUser.getId());

    ExecutorService executor = Executors.newFixedThreadPool(5);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(products.size());
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 5개 상품을 동시에 주문 시도 (잔액은 30,000원이므로 3개만 성공 가능)
    for (int i = 0; i < products.size(); i++) {
      final Product product = products.get(i);
      executor.submit(() -> {
        try {
          startLatch.await();

          String addToCartRequest = String.format(
              "{\"productId\": %d, \"quantity\": 1}",
              product.getId());

          MvcResult cartResult = mockMvc.perform(
              post("/carts/items")
                  .header("Authorization", "Bearer " + generateToken(testUser))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(addToCartRequest))
              .andReturn();

          if (cartResult.getResponse().getStatus() == 201) {
            Long cartItemId = extractCartItemId(cartResult.getResponse().getContentAsString());

            MvcResult orderResult = mockMvc.perform(
                post("/orders")
                    .header("Authorization", "Bearer " + generateToken(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createOrderRequest(cartItemId)))
                .andReturn();

            if (orderResult.getResponse().getStatus() == 201) {
              Long orderId = extractOrderId(orderResult.getResponse().getContentAsString());

              MvcResult paymentResult = mockMvc.perform(
                  post("/orders/{orderId}/payment", orderId)
                      .header("Authorization", "Bearer " + generateToken(testUser))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"paymentMethod\": \"POINT\"}"))
                  .andReturn();

              if (paymentResult.getResponse().getStatus() == 200) {
                successCount.incrementAndGet();
              }
            }
          }
        } catch (Exception e) {
          // 잔액 부족으로 실패는 정상
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 3개만 성공 (30,000원으로 10,000원짜리 3개 구매 가능)
    assertThat(successCount.get()).isEqualTo(3);

    // 사용자의 잔액 확인 (30,000 - 30,000 = 0)
    User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
    assertThat(updatedUser.getAvailablePoint().getValue()).isEqualTo(0);
  }

  @Test
  @DisplayName("대규모 동시 결제 - 1,000,000원으로 50개 상품(각 10,000원) 동시 구매")
  void testLargeScaleConcurrentPayments() throws Exception {
    // given: 1명의 사용자 (1,000,000원)와 50개 상품
    // NOTE: CartController가 현재 hardcoded userId=1을 사용
    final User testUser = createUserInNewTransaction("test3@example.com", "테스트유저3", 1000000);
    List<Product> products = createProductsInNewTransaction(50, 10000, 100);

    // 카트 미리 생성 (동시성 문제 회피)
    createCartInNewTransaction(testUser.getId());

    ExecutorService executor = Executors.newFixedThreadPool(50);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(products.size());
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 50개 상품을 동시에 주문
    for (int i = 0; i < products.size(); i++) {
      final Product product = products.get(i);
      executor.submit(() -> {
        try {
          startLatch.await();

          String addToCartRequest = String.format(
              "{\"productId\": %d, \"quantity\": 1}",
              product.getId());

          MvcResult cartResult = mockMvc.perform(
              post("/carts/items")
                  .header("Authorization", "Bearer " + generateToken(testUser))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(addToCartRequest))
              .andReturn();

          if (cartResult.getResponse().getStatus() == 201) {
            Long cartItemId = extractCartItemId(cartResult.getResponse().getContentAsString());

            MvcResult orderResult = mockMvc.perform(
                post("/orders")
                    .header("Authorization", "Bearer " + generateToken(testUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createOrderRequest(cartItemId)))
                .andReturn();

            if (orderResult.getResponse().getStatus() == 201) {
              Long orderId = extractOrderId(orderResult.getResponse().getContentAsString());

              MvcResult paymentResult = mockMvc.perform(
                  post("/orders/{orderId}/payment", orderId)
                      .header("Authorization", "Bearer " + generateToken(testUser))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"paymentMethod\": \"POINT\"}"))
                  .andReturn();

              if (paymentResult.getResponse().getStatus() == 200) {
                successCount.incrementAndGet();
              }
            }
          }
        } catch (Exception e) {
          // 실패 가능
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(60, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 50개 모두 성공
    assertThat(successCount.get()).isEqualTo(50);

    // 사용자의 잔액 확인 (1,000,000 - 500,000 = 500,000)
    User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
    assertThat(updatedUser.getAvailablePoint().getValue()).isEqualTo(500000);
  }
}
