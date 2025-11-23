package com.hanghae.ecommerce.concurrency;

import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simple test to debug cart addition
 */
@DisplayName("장바구니 추가 디버그 테스트")
class CartAdditionDebugTest extends BaseConcurrencyTest {

  @Test
  @DisplayName("단일 장바구니 추가 테스트")
  void testSingleCartAddition() throws Exception {
    // given
    User user = createUser("cart_debug@test.com", "디버그테스트", 100000);
    List<Product> products = createProducts(1, 10000, 100);
    Product product = products.get(0);

    // when: 장바구니에 추가
    String addToCartRequest = String.format(
        "{\"productId\": %d, \"quantity\": 1}",
        product.getId());

    MvcResult result = mockMvc.perform(
        post("/carts/items")
            .header("Authorization", "Bearer " + generateToken(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(addToCartRequest))
        .andExpect(status().isCreated())
        .andReturn();

    System.out.println("Response status: " + result.getResponse().getStatus());
    System.out.println("Response body: " + result.getResponse().getContentAsString());
  }
}
