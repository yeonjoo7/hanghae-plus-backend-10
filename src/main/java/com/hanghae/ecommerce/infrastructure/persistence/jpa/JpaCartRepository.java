package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartState;
import com.hanghae.ecommerce.domain.cart.repository.CartRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
        if (cart.getId() == null) {
            return insert(cart);
        } else {
            return update(cart);
        }
    }
    
    private Cart insert(Cart cart) {
        String sql = """
            INSERT INTO carts (user_id, user_coupon_id, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, cart.getUserId());
            if (cart.getUserCouponId() != null) {
                ps.setLong(2, cart.getUserCouponId());
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }
            ps.setString(3, cart.getState().name());
            ps.setTimestamp(4, Timestamp.valueOf(cart.getCreatedAt()));
            ps.setTimestamp(5, Timestamp.valueOf(cart.getUpdatedAt()));
            return ps;
        }, keyHolder);
        
        Long id = keyHolder.getKey().longValue();
        return Cart.restore(id, cart.getUserId(), cart.getUserCouponId(), cart.getState(), 
                           cart.getCreatedAt(), cart.getUpdatedAt());
    }
    
    private Cart update(Cart cart) {
        String sql = """
            UPDATE carts 
            SET user_coupon_id = ?, status = ?, updated_at = ?
            WHERE id = ?
            """;
        
        jdbcTemplate.update(sql, 
                           cart.getUserCouponId(),
                           cart.getState().name(), 
                           Timestamp.valueOf(cart.getUpdatedAt()), 
                           cart.getId());
        return cart;
    }
    
    @Override
    public Optional<Cart> findById(Long id) {
        String sql = """
            SELECT id, user_id, user_coupon_id, status, created_at, updated_at
            FROM carts 
            WHERE id = ?
            """;
        
        try {
            Cart cart = jdbcTemplate.queryForObject(sql, cartRowMapper(), id);
            return Optional.of(cart);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    private RowMapper<Cart> cartRowMapper() {
        return (rs, rowNum) -> {
            Long userCouponId = rs.getLong("user_coupon_id");
            if (rs.wasNull()) {
                userCouponId = null;
            }
            return Cart.restore(
                rs.getLong("id"),
                rs.getLong("user_id"), 
                userCouponId,
                CartState.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            );
        };
    }
    
    @Override
    public Optional<Cart> findByUserId(Long userId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<Cart> findByUserIdAndState(Long userId, CartState state) {
        String sql = """
            SELECT id, user_id, user_coupon_id, status, created_at, updated_at
            FROM carts 
            WHERE user_id = ? AND status = ?
            """;
        
        try {
            Cart cart = jdbcTemplate.queryForObject(sql, cartRowMapper(), userId, state.name());
            return Optional.of(cart);
        } catch (Exception e) {
            return Optional.empty();
        }
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