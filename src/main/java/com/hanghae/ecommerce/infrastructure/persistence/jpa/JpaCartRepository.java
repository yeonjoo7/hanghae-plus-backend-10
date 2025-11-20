package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartState;
import com.hanghae.ecommerce.domain.cart.repository.CartRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaCartRepository implements CartRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public JpaCartRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public Cart save(Cart cart) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<Cart> findById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<Cart> findByUserId(Long userId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<Cart> findByUserIdAndState(Long userId, CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<Cart> findActiveCartByUserId(Long userId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Cart> findCartsWithCoupon() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Cart> findByUserCouponId(Long userCouponId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Cart> findByState(CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Cart> findAll() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsByUserId(Long userId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsActiveCartByUserId(Long userId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public void deleteById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public void deleteByUserId(Long userId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long count() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long countByUserId(Long userId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long countByState(CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
}