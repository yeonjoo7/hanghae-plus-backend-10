package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.application.product.StockService;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LockManager lockManager;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @InjectMocks
    private StockService stockService;

    private Product testProduct;
    private Stock testStock;

    @BeforeEach
    void setUp() {
        testProduct = Product.create(
            "테스트 상품",
            "테스트 상품 설명",
            Money.of(10000),
            null // 구매 제한 없음
        );

        testStock = Stock.createForProduct(1L, Quantity.of(100), null);
    }

    @Test
    @DisplayName("재고 조회 성공")
    void getStock_Success() {
        // given
        Long productId = 1L;
        when(stockRepository.findByProductIdAndProductOptionIdIsNull(productId))
            .thenReturn(Optional.of(testStock));

        // when
        Stock result = stockService.getStock(productId);

        // then
        assertThat(result).isEqualTo(testStock);
        assertThat(result.getAvailableQuantity().getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("존재하지 않는 상품 재고 조회 실패")
    void getStock_NotFound() {
        // given
        Long productId = 1L;
        when(stockRepository.findByProductIdAndProductOptionIdIsNull(productId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> stockService.getStock(productId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("재고를 찾을 수 없습니다. ProductID: " + productId);
    }

    @Test
    @DisplayName("재고 차감 성공")
    void reduceStock_Success() {
        // given
        Long productId = 1L;
        int quantity = 10;

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(stockRepository.findByProductIdAndProductOptionIdIsNullForUpdate(productId))
            .thenReturn(Optional.of(testStock));
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        stockService.reduceStock(productId, quantity);

        // then
        assertThat(testStock.getAvailableQuantity().getValue()).isEqualTo(90);
        assertThat(testStock.getSoldQuantity().getValue()).isEqualTo(10);
        verify(stockRepository).save(testStock);
    }

    @Test
    @DisplayName("재고 부족 시 차감 실패")
    void reduceStock_InsufficientStock() {
        // given
        Long productId = 1L;
        int quantity = 150; // 재고(100)보다 많은 수량

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(stockRepository.findByProductIdAndProductOptionIdIsNullForUpdate(productId))
            .thenReturn(Optional.of(testStock));

        // when & then
        assertThatThrownBy(() -> stockService.reduceStock(productId, quantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("잘못된 수량으로 재고 차감 실패")
    void reduceStock_InvalidQuantity() {
        // given
        Long productId = 1L;
        int quantity = -5; // 음수 수량

        // when & then
        assertThatThrownBy(() -> stockService.reduceStock(productId, quantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("차감할 수량은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("재고 복원 성공")
    void restoreStock_Success() {
        // given
        Long productId = 1L;
        int quantity = 10;
        
        // 먼저 재고를 차감된 상태로 만들기
        testStock.reduceStock(Quantity.of(20));

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(stockRepository.findByProductIdAndProductOptionIdIsNullForUpdate(productId))
            .thenReturn(Optional.of(testStock));
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        stockService.restoreStock(productId, quantity);

        // then
        assertThat(testStock.getAvailableQuantity().getValue()).isEqualTo(90); // 80에서 10 복원
        assertThat(testStock.getSoldQuantity().getValue()).isEqualTo(10); // 20에서 10 복원
        verify(stockRepository).save(testStock);
    }

    @Test
    @DisplayName("복원할 재고가 부족한 경우 실패")
    void restoreStock_InsufficientSoldStock() {
        // given
        Long productId = 1L;
        int quantity = 10;
        // 판매 수량이 0인 상태

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(stockRepository.findByProductIdAndProductOptionIdIsNullForUpdate(productId))
            .thenReturn(Optional.of(testStock));

        // when & then
        assertThatThrownBy(() -> stockService.restoreStock(productId, quantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("복원할 재고가 판매된 수량보다 클 수 없습니다");
    }

    @Test
    @DisplayName("잘못된 수량으로 재고 복원 실패")
    void restoreStock_InvalidQuantity() {
        // given
        Long productId = 1L;
        int quantity = -5; // 음수 수량

        // when & then
        assertThatThrownBy(() -> stockService.restoreStock(productId, quantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("복구할 수량은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("존재하지 않는 상품 재고 차감 실패")
    void reduceStock_StockNotFound() {
        // given
        Long productId = 1L;
        int quantity = 10;

        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            var task = (LockManager.LockTask<?>) invocation.getArgument(1);
            return task.execute();
        });
        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(stockRepository.findByProductIdAndProductOptionIdIsNullForUpdate(productId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> stockService.reduceStock(productId, quantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("재고를 찾을 수 없습니다. ProductID: " + productId);
    }
}