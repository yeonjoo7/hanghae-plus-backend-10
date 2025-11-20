package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.order.OrderItem;
import com.hanghae.ecommerce.domain.order.OrderState;
import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Quantity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        if (orderItem.getId() == null) {
            return insert(orderItem);
        } else {
            return update(orderItem);
        }
    }
    
    private OrderItem insert(OrderItem orderItem) {
        String sql = """
            INSERT INTO order_items (
                order_id, product_id, product_option_id, product_name, 
                product_option_name, quantity, unit_price, subtotal, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, orderItem.getOrderId());
            ps.setLong(2, orderItem.getProductId());
            if (orderItem.getProductOptionId() != null) {
                ps.setLong(3, orderItem.getProductOptionId());
            } else {
                ps.setNull(3, java.sql.Types.BIGINT);
            }
            ps.setString(4, "상품명"); // 상품명은 별도 조회 필요
            ps.setNull(5, java.sql.Types.VARCHAR); // product_option_name
            ps.setInt(6, orderItem.getQuantity().getValue());
            ps.setBigDecimal(7, new java.math.BigDecimal(orderItem.getPrice().getValue()));
            ps.setBigDecimal(8, new java.math.BigDecimal(orderItem.getTotalAmount().getValue()));
            ps.setTimestamp(9, Timestamp.valueOf(orderItem.getCreatedAt()));
            return ps;
        }, keyHolder);
        
        Long id = keyHolder.getKey().longValue();
        return OrderItem.restore(
            id, orderItem.getOrderId(), orderItem.getProductId(), 
            orderItem.getProductOptionId(), orderItem.getState(), 
            orderItem.getPrice(), orderItem.getQuantity(), 
            orderItem.getDiscountAmount(), orderItem.getTotalAmount(),
            orderItem.getCreatedAt(), orderItem.getUpdatedAt()
        );
    }
    
    private OrderItem update(OrderItem orderItem) {
        // 주문 아이템은 일반적으로 update하지 않음
        return orderItem;
    }
    
    @Override
    public Optional<OrderItem> findById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        String sql = "SELECT * FROM order_items WHERE order_id = ?";
        return jdbcTemplate.query(sql, new OrderItemRowMapper(), orderId);
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
    @Transactional
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        List<OrderItem> savedItems = new ArrayList<>();
        for (OrderItem orderItem : orderItems) {
            savedItems.add(save(orderItem));
        }
        return savedItems;
    }
    
    @Override
    public List<ProductSalesInfo> findTopSellingProducts(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    private static class OrderItemRowMapper implements RowMapper<OrderItem> {
        @Override
        public OrderItem mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
            return OrderItem.restore(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getLong("product_id"),
                rs.getObject("product_option_id") != null ? rs.getLong("product_option_id") : null,
                OrderState.COMPLETED, // 기본값으로 COMPLETED 상태 설정
                Money.of(rs.getInt("unit_price")),
                Quantity.of(rs.getInt("quantity")),
                Money.of(0), // discount_amount는 0으로 설정
                Money.of(rs.getInt("subtotal")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime() // updated_at는 created_at과 동일하게 설정
            );
        }
    }
}