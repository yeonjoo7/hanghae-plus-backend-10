package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.order.*;
import com.hanghae.ecommerce.domain.order.repository.OrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
        // TODO: 구현 필요
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.empty();
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
}