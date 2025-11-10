package com.hanghae.ecommerce.domain.order.repository;

import com.hanghae.ecommerce.domain.order.OrderItem;
import com.hanghae.ecommerce.domain.order.OrderState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 아이템 Repository 인터페이스
 */
public interface OrderItemRepository {
    
    /**
     * 주문 아이템 저장
     */
    OrderItem save(OrderItem orderItem);
    
    /**
     * ID로 주문 아이템 조회
     */
    Optional<OrderItem> findById(Long id);
    
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
    List<OrderItem> findItemsWithDiscount();
    
    /**
     * 모든 주문 아이템 조회
     */
    List<OrderItem> findAll();
    
    /**
     * 주문 아이템 존재 여부 확인
     */
    boolean existsById(Long id);
    
    /**
     * 주문에 아이템 존재 여부 확인
     */
    boolean existsByOrderId(Long orderId);
    
    /**
     * 특정 상품/옵션이 주문에 있는지 확인
     */
    boolean existsByOrderIdAndProductIdAndProductOptionId(Long orderId, Long productId, Long productOptionId);
    
    /**
     * 주문 아이템 삭제
     */
    void deleteById(Long id);
    
    /**
     * 특정 주문의 모든 아이템 삭제
     */
    void deleteByOrderId(Long orderId);
    
    /**
     * 특정 상품의 모든 주문 아이템 삭제
     */
    void deleteByProductId(Long productId);
    
    /**
     * 전체 주문 아이템 수 조회
     */
    long count();
    
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
     * 주문 아이템 배치 저장
     */
    List<OrderItem> saveAll(List<OrderItem> orderItems);
    
    /**
     * 베스트셀러 상품 조회
     */
    List<ProductSalesInfo> findTopSellingProducts(LocalDateTime startDate, LocalDateTime endDate, int limit);
    
    /**
     * 상품 판매 정보 클래스
     */
    class ProductSalesInfo {
        private final Long productId;
        private final Long totalQuantity;
        private final Long totalAmount;
        
        public ProductSalesInfo(Long productId, Long totalQuantity, Long totalAmount) {
            this.productId = productId;
            this.totalQuantity = totalQuantity;
            this.totalAmount = totalAmount;
        }
        
        public Long getProductId() { return productId; }
        public Long getTotalQuantity() { return totalQuantity; }
        public Long getTotalAmount() { return totalAmount; }
    }
}