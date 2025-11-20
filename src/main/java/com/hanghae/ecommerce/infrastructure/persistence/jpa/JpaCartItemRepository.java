package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.cart.CartItem;
import com.hanghae.ecommerce.domain.cart.CartState;
import com.hanghae.ecommerce.domain.cart.repository.CartItemRepository;
import com.hanghae.ecommerce.domain.product.Quantity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class JpaCartItemRepository implements CartItemRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public JpaCartItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public CartItem save(CartItem cartItem) {
        if (cartItem.getId() == null) {
            return insert(cartItem);
        } else {
            return update(cartItem);
        }
    }
    
    private CartItem insert(CartItem cartItem) {
        String sql = """
            INSERT INTO cart_items (cart_id, product_id, product_option_id, quantity, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, cartItem.getCartId());
            ps.setLong(2, cartItem.getProductId());
            if (cartItem.getProductOptionId() != null) {
                ps.setLong(3, cartItem.getProductOptionId());
            } else {
                ps.setNull(3, java.sql.Types.BIGINT);
            }
            ps.setInt(4, cartItem.getQuantity().getValue());
            ps.setTimestamp(5, Timestamp.valueOf(cartItem.getCreatedAt()));
            ps.setTimestamp(6, Timestamp.valueOf(cartItem.getUpdatedAt()));
            return ps;
        }, keyHolder);
        
        Long id = keyHolder.getKey().longValue();
        return CartItem.restore(id, cartItem.getCartId(), cartItem.getProductId(), 
                               cartItem.getProductOptionId(), cartItem.getState(), 
                               cartItem.getQuantity(), cartItem.getCreatedAt(), cartItem.getUpdatedAt());
    }
    
    private CartItem update(CartItem cartItem) {
        String sql = """
            UPDATE cart_items 
            SET quantity = ?, updated_at = ?
            WHERE id = ?
            """;
        
        jdbcTemplate.update(sql, cartItem.getQuantity().getValue(), 
                           Timestamp.valueOf(cartItem.getUpdatedAt()), 
                           cartItem.getId());
        return cartItem;
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
        String sql = """
            SELECT id, cart_id, product_id, product_option_id, quantity, created_at, updated_at
            FROM cart_items 
            WHERE cart_id = ?
            """;
        
        return jdbcTemplate.query(sql, cartItemRowMapper(), cartId);
    }
    
    private RowMapper<CartItem> cartItemRowMapper() {
        return (rs, rowNum) -> {
            Long productOptionId = rs.getLong("product_option_id");
            if (rs.wasNull()) {
                productOptionId = null;
            }
            return CartItem.restore(
                rs.getLong("id"),
                rs.getLong("cart_id"), 
                rs.getLong("product_id"),
                productOptionId,
                CartState.NORMAL, // cart_items 테이블에는 state 컬럼이 없으므로 기본값 사용
                Quantity.of(rs.getInt("quantity")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            );
        };
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
        if (ids.isEmpty()) {
            return List.of();
        }
        
        String placeholders = ids.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));
        
        String sql = String.format("""
            SELECT id, cart_id, product_id, product_option_id, quantity, created_at, updated_at
            FROM cart_items 
            WHERE id IN (%s) AND cart_id = ?
            """, placeholders);
        
        Object[] params = new Object[ids.size() + 1];
        for (int i = 0; i < ids.size(); i++) {
            params[i] = ids.get(i);
        }
        params[ids.size()] = cartId;
        
        return jdbcTemplate.query(sql, cartItemRowMapper(), params);
    }
    
    @Override
    public Optional<CartItem> findByIdAndCartIdAndState(Long id, Long cartId, CartState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
}