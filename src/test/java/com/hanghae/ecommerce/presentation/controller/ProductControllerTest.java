package com.hanghae.ecommerce.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanghae.ecommerce.EcommerceApiApplication;
import com.hanghae.ecommerce.application.service.ProductService;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProductController.class)
@ContextConfiguration(classes = EcommerceApiApplication.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;


    @Test
    @DisplayName("상품 상세 조회 성공")
    void getProduct_Success() throws Exception {
        // given
        Long productId = 1L;
        Product product = Product.create("테스트 상품", "상품 설명", Money.of(15000), Quantity.of(10));
        ProductService.ProductWithStock productWithStock = new ProductService.ProductWithStock(product, null);
        
        when(productService.getProductWithStock(productId)).thenReturn(productWithStock);

        // when & then
        mockMvc.perform(get("/products/{productId}", productId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("테스트 상품"))
                .andExpect(jsonPath("$.data.description").value("상품 설명"))
                .andExpect(jsonPath("$.data.price").value(15000));

        verify(productService).getProductWithStock(productId);
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 실패")
    void getProduct_NotFound() throws Exception {
        // given
        Long productId = 999L;
        
        when(productService.getProductWithStock(productId))
            .thenThrow(new IllegalArgumentException("상품을 찾을 수 없습니다"));

        // when & then
        mockMvc.perform(get("/products/{productId}", productId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("상품을 찾을 수 없습니다"));

        verify(productService).getProductWithStock(productId);
    }

    @Test
    @DisplayName("인기 상품 목록 조회 성공")
    void getPopularProducts_Success() throws Exception {
        // given
        
        List<ProductService.PopularProduct> popularProducts = List.of(
            new ProductService.PopularProduct(
                1, Product.create("인기상품1", "설명1", Money.of(10000), Quantity.of(5)), 
                null, 100, java.time.LocalDate.now().minusDays(7), java.time.LocalDate.now()
            ),
            new ProductService.PopularProduct(
                2, Product.create("인기상품2", "설명2", Money.of(20000), Quantity.of(3)), 
                null, 80, java.time.LocalDate.now().minusDays(7), java.time.LocalDate.now()
            )
        );

        when(productService.getPopularProducts()).thenReturn(popularProducts);

        // when & then
        mockMvc.perform(get("/products/popular")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.products.length()").value(2));

        verify(productService).getPopularProducts();
    }

}