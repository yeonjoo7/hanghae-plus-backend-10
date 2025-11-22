package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.domain.product.Product;
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
 * 잔액 차감 동시성 테스트
 * MySQL 환경에서 실제 API 엔드포인트를 사용하여 포인트 잔액 차감의 동시성 제어를 검증합니다.
 */
@DisplayName("잔액 차감 동시성 테스트")
class BalanceDeductionConcurrencyTest extends BaseConcurrencyTest {

  @Test
  @DisplayName("동시 잔액 차감 시 정합성 확인 - 100,000원으로 5개 상품(각 10,000원) 구매")
  void testConcurrentBalanceDeduction() throws Exception {
    // given: 잔액 100,000원 사용자와 5개 상품 (각 10,000원)
    User user = createUser("balance_test@test.com", "잔액테스트", 100000);
    List<Product> products = createProducts(5, 10000, 100); // 각 10,000원, 재고 충분

    ExecutorService executor = Executors.newFixedThreadPool(5);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(products.size());
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 5개 상품을 동시에 주문 (총 50,000원 사용)
    for (Product product : products) {
      executor.submit(() -> {
        try {
          startLatch.await();

          // 1. 장바구니에 추가
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

            // 2. 주문 생성
            MvcResult orderResult = mockMvc.perform(
                post("/orders")
                    .header("Authorization", "Bearer " + generateToken(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createOrderRequest(cartItemId)))
                .andReturn();

            if (orderResult.getResponse().getStatus() == 201) {
              Long orderId = extractOrderId(orderResult.getResponse().getContentAsString());

              // 3. 결제
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
          // 실패 가능
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 모두 성공 (잔액 충분)
    assertThat(completed).isTrue();
    assertThat(successCount.get()).isEqualTo(5);

    // 잔액 확인: 50,000원 남아야 함
    User updatedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(updatedUser.getAvailablePoint().getValue()).isEqualTo(50000);
  }

  @Test
  @DisplayName("잔액 부족 시 일부만 성공 - 30,000원으로 5개 상품(각 10,000원) 구매 시도")
  void testConcurrentBalanceDeductionWithInsufficientBalance() throws Exception {
    // given: 잔액 30,000원 사용자와 5개 상품
    User user = createUser("insufficient@test.com", "부족테스트", 30000);
    List<Product> products = createProducts(5, 10000, 100);

    ExecutorService executor = Executors.newFixedThreadPool(5);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(products.size());
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 5개 상품을 동시에 주문 시도
    for (Product product : products) {
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
          // 잔액 부족으로 실패는 정상
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 3개만 성공 (30,000원 / 10,000원 = 3)
    assertThat(successCount.get()).isEqualTo(3);

    // 잔액 확인: 0원이어야 함
    User updatedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(updatedUser.getAvailablePoint().getValue()).isEqualTo(0);
  }

  @Test
  @DisplayName("대규모 동시 결제 - 1,000,000원으로 50개 상품(각 10,000원) 동시 구매")
  void testLargeScaleConcurrentPayments() throws Exception {
    // given: 잔액 1,000,000원 사용자와 50개 상품
    User user = createUser("large_scale@test.com", "대규모테스트", 1000000);
    List<Product> products = createProducts(50, 10000, 100);

    ExecutorService executor = Executors.newFixedThreadPool(50);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(products.size());
    AtomicInteger successCount = new AtomicInteger(0);

    // when: 50개 상품을 동시에 주문
    for (Product product : products) {
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
          // 실패 가능
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await(60, TimeUnit.SECONDS);
    executor.shutdown();

    // then: 모두 성공
    assertThat(successCount.get()).isEqualTo(50);

    // 잔액 확인: 500,000원 남아야 함
    User updatedUser = userRepository.findById(user.getId()).orElseThrow();
    assertThat(updatedUser.getAvailablePoint().getValue()).isEqualTo(500000);
  }
}
