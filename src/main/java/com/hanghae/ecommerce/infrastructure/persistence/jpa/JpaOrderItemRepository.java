package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.order.OrderItem;
import com.hanghae.ecommerce.domain.order.OrderState;
import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaOrderItemRepository implements OrderItemRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public JpaOrderItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public OrderItem save(OrderItem orderItem) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<OrderItem> findById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<OrderItem> findByOrderIdAndState(Long orderId, OrderState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<OrderItem> findByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<OrderItem> findByProductIdAndProductOptionId(Long productId, Long productOptionId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<OrderItem> findByOrderIdAndProductIdAndProductOptionId(Long orderId, Long productId, Long productOptionId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<OrderItem> findByState(OrderState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<OrderItem> findItemsWithDiscount() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<OrderItem> findAll() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsByOrderId(Long orderId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsByOrderIdAndProductIdAndProductOptionId(Long orderId, Long productId, Long productOptionId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public void deleteById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public void deleteByOrderId(Long orderId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public void deleteByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long count() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long countByOrderId(Long orderId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long countByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long countByState(OrderState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<ProductSalesInfo> findTopSellingProducts(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
}