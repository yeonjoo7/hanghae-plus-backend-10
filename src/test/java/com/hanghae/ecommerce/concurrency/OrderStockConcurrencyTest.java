package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 주문 및 재고 동시성 테스트
 * MySQL 환경에서 실제 API 엔드포인트를 사용하여 재고 차감의 동시성 제어를 검증합니다.
 */
@DisplayName("주문 및 재고 동시성 테스트")
class OrderStockConcurrencyTest extends BaseConcurrencyTest {

  @Autowired
  private com.hanghae.ecommerce.domain.payment.repository.PaymentRepository paymentRepository;

  @Autowired
  private com.hanghae.ecommerce.domain.order.repository.OrderRepository orderRepository;

  @Autowired
  private com.hanghae.ecommerce.domain.cart.repository.CartItemRepository cartItemRepository;

  @Autowired
  private com.hanghae.ecommerce.domain.cart.repository.CartRepository cartRepository;

  @Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  @AfterEach
  @Transactional
  void cleanup() {
    paymentRepository.deleteAll();
    orderRepository.deleteAll();
    cartItemRepository.deleteAll();
    cartRepository.deleteAll();
    stockRepository.deleteAll();
    productRepository.deleteAll();
    userRepository.deleteAll();

    // Reset auto-increment to ensure next user has ID 1
    jdbcTemplate.execute("ALTER TABLE users AUTO_INCREMENT = 1");
  }

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    // Reset auto-increment to ensure next user has ID 1
    jdbcTemplate.execute("ALTER TABLE users AUTO_INCREMENT = 1");
  }

  @Test
  @DisplayName("동시 주문 시 재고 정합성 확인 - 10명의 사용자가 1개 상품(재고 10개) 동시 주문")
  void testConcurrentOrderStockConsistency() throws Exception {
    // given: 1개 상품 (재고 10개)과 10명의 사용자
    System.out.println("TEST STARTED: testConcurrentOrderStockConsistency");

    // 1. 상품 생성 (재고 10개)
    Product product = createProductInNewTransaction("인기상품", 10000, 10);

    // 2. 사용자 10명 생성
    List<User> users = createUsersInNewTransaction(10);

    // 3. 각 사용자의 카트 생성
    createCartsInNewTransaction(users);

    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(10);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // when: 10명이 동시에 같은 상품 주문
    for (User user : users) {
      executor.submit(() -> {
        try {
          startLatch.await();

          // 1. 장바구니에 상품 추가
          String addToCartRequest = String.format(
              "{\"productId\": %d, \"quantity\": 1}",
              product.getId());

          MvcResult cartResult = mockMvc.perform(
              post("/carts/items")
                  .header("Authorization", "Bearer " + generateToken(user))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(addToCartRequest))
              .andReturn();

          if (cartResult.getResponse().getStatus() != 201) {
            System.out
                .println("Cart failed for user " + user.getId() + ": " + cartResult.getResponse().getContentAsString());
            failCount.incrementAndGet();
            return;
          }

          Long cartItemId = extractCartItemId(cartResult.getResponse().getContentAsString());

          // 2. 주문 생성
          String orderRequest = createOrderRequest(cartItemId);

          MvcResult orderResult = mockMvc.perform(
              post("/orders")
                  .header("Authorization", "Bearer " + generateToken(user))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(orderRequest))
              .andReturn();

          if (orderResult.getResponse().getStatus() != 201) {
            System.out.println(
                "Order failed for user " + user.getId() + ": " + orderResult.getResponse().getContentAsString());
            failCount.incrementAndGet();
            return;
          }

          Long orderId = extractOrderId(orderResult.getResponse().getContentAsString());

          // 3. 결제 처리
          String paymentRequest = "{\"paymentMethod\": \"POINT\"}";

          MvcResult paymentResult = mockMvc.perform(
              post("/orders/{orderId}/payment", orderId)
                  .header("Authorization", "Bearer " + generateToken(user))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(paymentRequest))
              .andReturn();

          if (paymentResult.getResponse().getStatus() == 200) {
            successCount.incrementAndGet();
          } else {
            System.out.println(
                "Payment failed for user " + user.getId() + ": " + paymentResult.getResponse().getContentAsString());
            failCount.incrementAndGet();
          }
        } catch (Exception e) {
          System.out.println("Exception: " + e.getMessage());
          e.printStackTrace();
          failCount.incrementAndGet();
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 모두 성공 (10개 재고, 10명 주문)
    assertThat(completed).isTrue();
    assertThat(successCount.get()).isEqualTo(10);
    assertThat(failCount.get()).isEqualTo(0);

    // 재고 확인: 재고 0
    Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNull(product.getId())
        .orElseThrow();
    assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(0);
  }

  @Test
  @DisplayName("재고 부족 시 동시 주문 처리 - 11명의 사용자가 1개 상품(재고 10개) 동시 주문")
  void testConcurrentOrderStockInsufficient() throws Exception {
    // given: 1개 상품 (재고 10개)과 11명의 사용자
    System.out.println("TEST STARTED: testConcurrentOrderStockInsufficient");

    // 1. 상품 생성 (재고 10개)
    Product product = createProductInNewTransaction("한정판상품", 10000, 10);

    // 2. 사용자 11명 생성
    List<User> users = createUsersInNewTransaction(11);

    // 3. 각 사용자의 카트 생성
    createCartsInNewTransaction(users);

    ExecutorService executor = Executors.newFixedThreadPool(11);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(11);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // when: 11명이 동시에 같은 상품 주문
    for (User user : users) {
      executor.submit(() -> {
        try {
          startLatch.await();

          // 1. 장바구니에 상품 추가
          String addToCartRequest = String.format(
              "{\"productId\": %d, \"quantity\": 1}",
              product.getId());

          MvcResult cartResult = mockMvc.perform(
              post("/carts/items")
                  .header("Authorization", "Bearer " + generateToken(user))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(addToCartRequest))
              .andReturn();

          if (cartResult.getResponse().getStatus() != 201) {
            // 장바구니 담기 실패는 재고 부족일 수 있음 (컨트롤러에서 체크하므로)
            failCount.incrementAndGet();
            return;
          }

          Long cartItemId = extractCartItemId(cartResult.getResponse().getContentAsString());

          // 2. 주문 생성
          String orderRequest = createOrderRequest(cartItemId);

          MvcResult orderResult = mockMvc.perform(
              post("/orders")
                  .header("Authorization", "Bearer " + generateToken(user))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(orderRequest))
              .andReturn();

          if (orderResult.getResponse().getStatus() == 201) {
            Long orderId = extractOrderId(orderResult.getResponse().getContentAsString());

            // 3. 결제 처리
            MvcResult paymentResult = mockMvc.perform(
                post("/orders/{orderId}/payment", orderId)
                    .header("Authorization", "Bearer " + generateToken(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"paymentMethod\": \"POINT\"}"))
                .andReturn();

            if (paymentResult.getResponse().getStatus() == 200) {
              successCount.incrementAndGet();
            } else {
              failCount.incrementAndGet();
            }
          } else {
            // 주문 생성 실패 (재고 부족 등)
            failCount.incrementAndGet();
          }
        } catch (Exception e) {
          failCount.incrementAndGet();
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 10명 성공, 1명 실패
    assertThat(completed).isTrue();
    assertThat(successCount.get()).isEqualTo(10);
    assertThat(failCount.get()).isEqualTo(1);

    // 재고 확인: 재고 0
    Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNull(product.getId())
        .orElseThrow();
    assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(0);
  }
}
