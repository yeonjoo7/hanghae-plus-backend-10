package com.hanghae.ecommerce.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StockTest {

    @Test
    @DisplayName("재고 생성 성공")
    void create_Success() {
        // given
        Long productId = 1L;
        Quantity initialQuantity = Quantity.of(100);

        // when
        Stock stock = Stock.createForProduct(productId, initialQuantity, null);

        // then
        assertThat(stock.getProductId()).isEqualTo(productId);
        assertThat(stock.getAvailableQuantity()).isEqualTo(initialQuantity);
        assertThat(stock.getSoldQuantity().getValue()).isZero();
        assertThat(stock.getTotalQuantity()).isEqualTo(initialQuantity);
    }

    @Test
    @DisplayName("음수 수량으로 재고 생성 실패")
    void create_NegativeQuantity() {
        // given
        Long productId = 1L;

        // when & then
        assertThatThrownBy(() -> Stock.createForProduct(productId, Quantity.of(-10), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("수량은 0 이상이어야 합니다: -10");
    }

    @Test
    @DisplayName("재고 차감 성공")
    void reduceStock_Success() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(100), null);
        Quantity reduceQuantity = Quantity.of(30);

        // when
        stock.reduceStock(reduceQuantity);

        // then
        assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(70);
        assertThat(stock.getSoldQuantity().getValue()).isEqualTo(30);
        assertThat(stock.getTotalQuantity().getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("재고 부족 시 차감 실패")
    void reduceStock_InsufficientStock() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(50), null);
        Quantity excessiveQuantity = Quantity.of(60);

        // when & then
        assertThatThrownBy(() -> stock.reduceStock(excessiveQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("재고가 부족합니다. 요청: 60, 사용가능: 50");

        // 재고는 변경되지 않아야 함
        assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(50);
        assertThat(stock.getSoldQuantity().getValue()).isZero();
    }

    @Test
    @DisplayName("0 이하 수량으로 재고 차감 실패")
    void reduceStock_InvalidQuantity() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(100), null);

        // when & then
        assertThatThrownBy(() -> stock.reduceStock(Quantity.of(0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("차감할 재고는 0보다 커야 합니다");

        assertThatThrownBy(() -> stock.reduceStock(Quantity.of(-5)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("수량은 0 이상이어야 합니다: -5");
    }

    @Test
    @DisplayName("재고 복원 성공")
    void restoreStock_Success() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(100), null);
        stock.reduceStock(Quantity.of(30)); // 30개 차감 (가용: 70, 판매: 30)
        
        Quantity restoreQuantity = Quantity.of(10);

        // when
        stock.restoreStock(restoreQuantity);

        // then
        assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(80);
        assertThat(stock.getSoldQuantity().getValue()).isEqualTo(20);
        assertThat(stock.getTotalQuantity().getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("복원할 재고가 부족한 경우 실패")
    void restoreStock_InsufficientSoldStock() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(100), null);
        stock.reduceStock(Quantity.of(20)); // 20개만 판매
        
        Quantity excessiveRestoreQuantity = Quantity.of(30); // 30개 복원 시도

        // when & then
        assertThatThrownBy(() -> stock.restoreStock(excessiveRestoreQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("복원할 재고가 판매된 수량보다 클 수 없습니다. 요청: 30, 판매됨: 20");

        // 재고는 변경되지 않아야 함
        assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(80);
        assertThat(stock.getSoldQuantity().getValue()).isEqualTo(20);
    }

    @Test
    @DisplayName("0 이하 수량으로 재고 복원 실패")
    void restoreStock_InvalidQuantity() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(100), null);
        stock.reduceStock(Quantity.of(20));

        // when & then
        assertThatThrownBy(() -> stock.restoreStock(Quantity.of(0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("복원할 재고는 0보다 커야 합니다");

        assertThatThrownBy(() -> stock.restoreStock(Quantity.of(-5)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("수량은 0 이상이어야 합니다: -5");
    }

    @Test
    @DisplayName("재고 증가 성공")
    void increaseStock_Success() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(100), null);
        Quantity increaseQuantity = Quantity.of(50);

        // when
        stock.addStock(increaseQuantity);

        // then
        assertThat(stock.getAvailableQuantity().getValue()).isEqualTo(150);
        assertThat(stock.getSoldQuantity().getValue()).isZero();
        assertThat(stock.getTotalQuantity().getValue()).isEqualTo(150);
    }

    @Test
    @DisplayName("0 이하 수량으로 재고 증가 실패")
    void increaseStock_InvalidQuantity() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(100), null);

        // when & then
        assertThatThrownBy(() -> stock.addStock(Quantity.of(0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("추가할 재고는 0보다 커야 합니다");

        assertThatThrownBy(() -> stock.addStock(Quantity.of(-10)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("수량은 0 이상이어야 합니다: -10");
    }

    @Test
    @DisplayName("재고 충분 여부 확인")
    void hasEnoughStock() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(50), null);

        // when & then
        assertThat(stock.hasEnoughStock(Quantity.of(30))).isTrue(); // 충분
        assertThat(stock.hasEnoughStock(Quantity.of(50))).isTrue(); // 딱 맞음
        assertThat(stock.hasEnoughStock(Quantity.of(60))).isFalse();  // 부족
    }

    @Test
    @DisplayName("재고 비어있음 여부 확인")
    void isEmpty() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(10), null);

        // when & then
        assertThat(stock.isEmpty()).isFalse();

        stock.reduceStock(Quantity.of(10)); // 모든 재고 차감
        assertThat(stock.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("재고 가용 여부 확인")
    void isNotEmpty() {
        // given
        Stock stock = Stock.createForProduct(1L, Quantity.of(10), null);

        // when & then
        assertThat(!stock.isEmpty()).isTrue();

        stock.reduceStock(Quantity.of(10)); // 모든 재고 차감
        assertThat(!stock.isEmpty()).isFalse();
    }
}