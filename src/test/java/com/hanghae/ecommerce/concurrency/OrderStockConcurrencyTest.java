package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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

  @Test
  @DisplayName("동시 주문 시 재고 정합성 확인 - 5개 재고에 10명 주문 시 5명만 성공")
  void testConcurrentOrderStockConsistency() throws Exception {
    // given: 재고 5개 상품과 10명의 사용자
    Product product = createProduct("한정판 상품", 10000, 5);
    List<User> users = createUsers(10);

    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(users.size());
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 10명이 동시에 1개씩 주문
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
            return; // 장바구니 추가 실패
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
            return; // 주문 생성 실패 (재고 부족 등)
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
          }
        } catch (Exception e) {
          // 재고 부족 등으로 실패는 정상
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 정확히 5명만 성공
    assertThat(completed).isTrue();
    assertThat(successCount.get()).isEqualTo(5);

    // 재고 확인: 0이어야 함
    Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNull(product.getId())
        .orElseThrow();
    assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(0);
  }

  @Test
  @DisplayName("대량 동시 주문 - 100개 재고에 200명 주문 시 100명만 성공")
  void testLargeScaleConcurrentOrders() throws Exception {
    // given: 재고 100개 상품과 200명의 사용자
    Product product = createProduct("인기 상품", 50000, 100);
    List<User> users = createUsers(200);

    ExecutorService executor = Executors.newFixedThreadPool(50);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(users.size());
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 200명이 동시에 1개씩 주문
    for (User user : users) {
      executor.submit(() -> {
        try {
          startLatch.await();

          // 장바구니 -> 주문 -> 결제 플로우
          String addToCartRequest = String.format(
              "{\"productId\": %d, \"quantity\": 1}",
              product.getId());

          MvcResult cartResult = mockMvc.perform(
              post("/carts/items")
                  .header("Authorization", "Bearer " + generateToken(user))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(addToCartRequest))
              .andReturn();

          if (cartResult.getResponse().getStatus() == 201) {
            Long cartItemId = extractCartItemId(cartResult.getResponse().getContentAsString());
            String orderRequest = createOrderRequest(cartItemId);

            MvcResult orderResult = mockMvc.perform(
                post("/orders")
                    .header("Authorization", "Bearer " + generateToken(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(orderRequest))
                .andReturn();

            if (orderResult.getResponse().getStatus() == 201) {
              Long orderId = extractOrderId(orderResult.getResponse().getContentAsString());

              MvcResult paymentResult = mockMvc.perform(
                  post("/orders/{orderId}/payment", orderId)
                      .header("Authorization", "Bearer " + generateToken(user))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"paymentMethod\": \"POINT\"}"))
                  .andReturn();

              if (paymentResult.getResponse().getStatus() == 200) {
                successCount.incrementAndGet();
              }
            }
          }
        } catch (Exception e) {
          // 실패는 정상
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(60, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 정확히 100명만 성공
    assertThat(successCount.get()).isEqualTo(100);

    Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNull(product.getId())
        .orElseThrow();
    assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(0);
  }

  @Test
  @DisplayName("여러 상품 동시 주문 - 각 상품별 재고 정합성 확인")
  void testMultipleProductsConcurrentOrders() throws Exception {
    // given: 3개 상품 (각각 재고 10개)
    List<Product> products = createProducts(3, 20000, 10);
    List<User> users = createUsers(30); // 각 상품당 10명씩

    ExecutorService executor = Executors.newFixedThreadPool(30);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(users.size());
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 각 상품에 10명씩 동시 주문
    for (int i = 0; i < users.size(); i++) {
      final User user = users.get(i);
      final Product product = products.get(i / 10); // 10명씩 같은 상품 주문

      executor.submit(() -> {
        try {
          startLatch.await();

          String addToCartRequest = String.format(
              "{\"productId\": %d, \"quantity\": 1}",
              product.getId());

          MvcResult cartResult = mockMvc.perform(
              post("/carts/items")
                  .header("Authorization", "Bearer " + generateToken(user))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(addToCartRequest))
              .andReturn();

          if (cartResult.getResponse().getStatus() == 201) {
            Long cartItemId = extractCartItemId(cartResult.getResponse().getContentAsString());
            MvcResult orderResult = mockMvc.perform(
                post("/orders")
                    .header("Authorization", "Bearer " + generateToken(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createOrderRequest(cartItemId)))
                .andReturn();

            if (orderResult.getResponse().getStatus() == 201) {
              Long orderId = extractOrderId(orderResult.getResponse().getContentAsString());
              MvcResult paymentResult = mockMvc.perform(
                  post("/orders/{orderId}/payment", orderId)
                      .header("Authorization", "Bearer " + generateToken(user))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"paymentMethod\": \"POINT\"}"))
                  .andReturn();

              if (paymentResult.getResponse().getStatus() == 200) {
                successCount.incrementAndGet();
              }
            }
          }
        } catch (Exception e) {
          // 실패는 정상
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(60, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 모든 상품 재고 소진 (총 30명 성공)
    assertThat(successCount.get()).isEqualTo(30);

    for (Product product : products) {
      Stock stock = stockRepository.findByProductIdAndProductOptionIdIsNull(product.getId())
          .orElseThrow();
      assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(0);
    }
  }
}
