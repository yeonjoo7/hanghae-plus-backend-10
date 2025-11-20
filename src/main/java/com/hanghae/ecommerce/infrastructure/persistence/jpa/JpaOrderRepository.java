package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.order.*;
import com.hanghae.ecommerce.domain.order.repository.OrderRepository;
import com.hanghae.ecommerce.domain.product.Money;
import org.springframework.context.annotation.Profile;
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
import java.util.List;
import java.util.Optional;

@Repository
public class JpaOrderRepository implements OrderRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public JpaOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    @Transactional
    public Order save(Order order) {
        if (order.getId() == null) {
            return insert(order);
        } else {
            return update(order);
        }
    }
    
    private Order insert(Order order) {
        String sql = """
            INSERT INTO orders (
                order_number, user_id, total_amount, discount_amount, final_amount, 
                status, coupon_id, shipping_address, recipient_name, recipient_phone, 
                ordered_at, paid_at, shipped_at, delivered_at, cancelled_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, order.getOrderNumber().getValue());
            ps.setLong(2, order.getUserId());
            ps.setBigDecimal(3, new java.math.BigDecimal(order.getAmount().getValue()));
            ps.setBigDecimal(4, new java.math.BigDecimal(order.getDiscountAmount().getValue()));
            ps.setBigDecimal(5, new java.math.BigDecimal(order.getTotalAmount().getValue()));
            ps.setString(6, order.getState().name());
            if (order.getUserCouponId() != null) {
                ps.setLong(7, order.getUserCouponId());
            } else {
                ps.setNull(7, java.sql.Types.BIGINT);
            }
            ps.setString(8, order.getAddress().getFullAddress());
            ps.setString(9, order.getRecipient().getName());
            ps.setString(10, order.getRecipient().getPhone());
            ps.setTimestamp(11, Timestamp.valueOf(order.getCreatedAt()));
            ps.setNull(12, java.sql.Types.TIMESTAMP); // paid_at
            ps.setNull(13, java.sql.Types.TIMESTAMP); // shipped_at
            ps.setNull(14, java.sql.Types.TIMESTAMP); // delivered_at
            ps.setNull(15, java.sql.Types.TIMESTAMP); // cancelled_at
            return ps;
        }, keyHolder);
        
        Long id = keyHolder.getKey().longValue();
        return Order.restore(
            id, order.getUserId(), order.getUserCouponId(), order.getCartId(),
            order.getOrderNumber(), order.getState(), order.getAmount(),
            order.getDiscountAmount(), order.getTotalAmount(), order.getRecipient(),
            order.getAddress(), order.getCreatedAt(), order.getUpdatedAt()
        );
    }
    
    private Order update(Order order) {
        String sql = """
            UPDATE orders 
            SET status = ?, updated_at = NOW()
            WHERE id = ?
            """;
        
        jdbcTemplate.update(sql, order.getState().name(), order.getId());
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        String sql = "SELECT * FROM orders WHERE id = ?";
        List<Order> orders = jdbcTemplate.query(sql, new OrderRowMapper(), id);
        return orders.isEmpty() ? Optional.empty() : Optional.of(orders.get(0));
    }

    @Override
    public Optional<Order> findByOrderNumber(OrderNumber orderNumber) {
        return Optional.empty();
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return List.of();
    }

    @Override
    public List<Order> findByUserIdAndState(Long userId, OrderState state) {
        return null;
    }

    @Override
    public Optional<Order> findByCartId(Long cartId) {
        return Optional.empty();
    }

    @Override
    public List<Order> findOrdersWithCoupon() {
        return null;
    }

    @Override
    public List<Order> findByUserCouponId(Long userCouponId) {
        return null;
    }

    @Override
    public List<Order> findByState(OrderState state) {
        return null;
    }

    @Override
    public List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return null;
    }

    @Override
    public List<Order> findPendingPaymentOrders() {
        return null;
    }

    @Override
    public List<Order> findCompletedOrders() {
        return null;
    }

    @Override
    public List<Order> findCancelledOrders() {
        return null;
    }

    public List<Order> findByUserIdAndStatus(Long userId, OrderState status) {
        return List.of();
    }

    public List<Order> findByStatus(OrderState status) {
        return List.of();
    }

    public List<Order> findByOrderDateBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return List.of();
    }
    
    @Override
    public List<Order> findByUserIdAndStateAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, OrderState status, LocalDateTime startDate, LocalDateTime endDate) {
        return List.of();
    }
    
    @Override
    public List<Order> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return List.of();
    }
    
    @Override
    public List<Order> findByUserIdAndStateOrderByCreatedAtDesc(Long userId, OrderState state) {
        return List.of();
    }

    @Override
    public List<Order> findAll() {
        return List.of();
    }

    @Override
    public boolean existsById(Long id) {
        return false;
    }

    @Override
    public boolean existsByOrderNumber(OrderNumber orderNumber) {
        return false;
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return false;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        // TODO: 구현 필요
    }

    @Override
    public void deleteByUserId(Long userId) {

    }

    @Override
    public long count() {
        return 0;
    }

    public long countByStatus(OrderState status) {
        return 0;
    }

    @Override
    public long countByUserId(Long userId) {
        return 0;
    }

    @Override
    public long countByState(OrderState state) {
        return 0;
    }

    @Override
    public long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return 0;
    }

    @Override
    public List<Order> findByUserIdOrderByCreatedAtDesc(Long userId) {
        return null;
    }
    
    private static class OrderRowMapper implements RowMapper<Order> {
        @Override
        public Order mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
            return Order.restore(
                rs.getLong("id"),                                                    // id
                rs.getLong("user_id"),                                               // userId
                rs.getObject("coupon_id") != null ? rs.getLong("coupon_id") : null, // userCouponId
                null,                                                                // cartId (주문 완료 후엔 null)
                OrderNumber.of(rs.getString("order_number")),                       // orderNumber
                OrderState.valueOf(rs.getString("status")),                         // state
                Money.of(rs.getInt("total_amount")),                                 // amount
                Money.of(rs.getInt("discount_amount")),                             // discountAmount
                Money.of(rs.getInt("final_amount")),                                // totalAmount
                Recipient.of(rs.getString("recipient_name"), rs.getString("recipient_phone")), // recipient
                Address.of("12345", rs.getString("shipping_address"), ""),          // address (zipCode와 detailAddress는 임시값)
                rs.getTimestamp("ordered_at").toLocalDateTime(),                    // createdAt
                rs.getTimestamp("ordered_at").toLocalDateTime()                     // updatedAt
            );
        }
    }
}