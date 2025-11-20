package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.cart.CartItem;
import com.hanghae.ecommerce.domain.cart.CartState;
import com.hanghae.ecommerce.domain.cart.repository.CartItemRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaCartItemRepository implements CartItemRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public JpaCartItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public CartItem save(CartItem cartItem) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<CartItem> findById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<CartItem> findByCartId(Long cartId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<CartItem> findByCartIdAndState(Long cartId, CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<CartItem> findActiveItemsByCartId(Long cartId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<CartItem> findByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<CartItem> findByProductIdAndProductOptionId(Long productId, Long productOptionId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<CartItem> findByCartIdAndProductIdAndProductOptionId(Long cartId, Long productId, Long productOptionId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<CartItem> findByState(CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<CartItem> findAll() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsByCartId(Long cartId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsByCartIdAndProductIdAndProductOptionId(Long cartId, Long productId, Long productOptionId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public void deleteById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public void deleteByCartId(Long cartId) {
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
    public long countByCartId(Long cartId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long countActiveItemsByCartId(Long cartId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long countByState(CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<CartItem> findByCartIdAndProductIdAndProductOptionIdIsNullAndState(Long cartId, Long productId, CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<CartItem> saveAll(List<CartItem> cartItems) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<CartItem> findByIdInAndCartIdAndState(List<Long> ids, Long cartId, CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<CartItem> findByIdAndCartIdAndState(Long id, Long cartId, CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
}