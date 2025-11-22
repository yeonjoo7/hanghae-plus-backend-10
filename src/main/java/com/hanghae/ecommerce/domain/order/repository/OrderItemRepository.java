package com.hanghae.ecommerce.domain.order.repository;

import com.hanghae.ecommerce.domain.order.OrderItem;
import com.hanghae.ecommerce.domain.order.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 아이템 Repository - Spring Data JPA
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * 주문 ID로 아이템 목록 조회
     */
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * 주문 ID와 상태로 아이템 목록 조회
     */
    List<OrderItem> findByOrderIdAndState(Long orderId, OrderState state);

    /**
     * 상품 ID로 주문 아이템 목록 조회
     */
    List<OrderItem> findByProductId(Long productId);

    /**
     * 상품 옵션 ID로 주문 아이템 목록 조회
     */
    List<OrderItem> findByProductIdAndProductOptionId(Long productId, Long productOptionId);

    /**
     * 특정 주문에서 동일 상품/옵션 아이템 조회
     */
    Optional<OrderItem> findByOrderIdAndProductIdAndProductOptionId(Long orderId, Long productId, Long productOptionId);

    /**
     * 상태별 주문 아이템 목록 조회
     */
    List<OrderItem> findByState(OrderState state);

    /**
     * 할인이 적용된 주문 아이템 목록 조회
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.discountAmount.amount > 0")
    List<OrderItem> findItemsWithDiscount();

    /**
     * 주문에 아이템 존재 여부 확인
     */
    boolean existsByOrderId(Long orderId);

    /**
     * 특정 상품/옵션이 주문에 있는지 확인
     */
    boolean existsByOrderIdAndProductIdAndProductOptionId(Long orderId, Long productId, Long productOptionId);

    /**
     * 특정 주문의 모든 아이템 삭제
     */
    @Modifying
    @Query("DELETE FROM OrderItem oi WHERE oi.orderId = :orderId")
    void deleteByOrderId(@Param("orderId") Long orderId);

    /**
     * 특정 상품의 모든 주문 아이템 삭제
     */
    @Modifying
    @Query("DELETE FROM OrderItem oi WHERE oi.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    /**
     * 주문별 아이템 수 조회
     */
    long countByOrderId(Long orderId);

    /**
     * 상품별 주문 아이템 수 조회
     */
    long countByProductId(Long productId);

    /**
     * 상태별 주문 아이템 수 조회
     */
    long countByState(OrderState state);

    /**
     * 베스트셀러 상품 조회
     */
    /*
     * @Query("SELECT oi.productId as productId, SUM(oi.quantity.value) as totalQuantity, SUM(oi.totalAmount.amount) as totalAmount "
     * +
     * "FROM OrderItem oi " +
     * "WHERE oi.createdAt BETWEEN :startDate AND :endDate " +
     * "GROUP BY oi.productId " +
     * "ORDER BY SUM(oi.quantity.value) DESC")
     * List<ProductSalesProjection> findTopSellingProducts(
     * 
     * @Param("startDate") LocalDateTime startDate,
     * 
     * @Param("endDate") LocalDateTime endDate);
     */

    /**
     * 상품 판매 정보 Projection
     */
    interface ProductSalesProjection {
        Long getProductId();

        Long getTotalQuantity();

        Long getTotalAmount();
    }
}