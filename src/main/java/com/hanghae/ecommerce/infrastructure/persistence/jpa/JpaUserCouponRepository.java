package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaUserCouponRepository implements UserCouponRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final UserCouponRowMapper userCouponRowMapper = new UserCouponRowMapper();
    
    public JpaUserCouponRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    @Transactional
    public UserCoupon save(UserCoupon userCoupon) {
        String sql = """
            INSERT INTO user_coupons (id, user_id, coupon_id, status, issued_at, used_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                used_at = VALUES(used_at)
            """;
        
        jdbcTemplate.update(sql,
            userCoupon.getId(),
            userCoupon.getUserId(),
            userCoupon.getCouponId(),
            userCoupon.getState().name(),
            Timestamp.valueOf(userCoupon.getIssuedAt()),
            userCoupon.getUsedAt() != null ? Timestamp.valueOf(userCoupon.getUsedAt()) : null,
            Timestamp.valueOf(userCoupon.getExpiresAt())
        );
        
        return userCoupon;
    }
    
    @Override
    public Optional<UserCoupon> findById(Long id) {
        String sql = "SELECT * FROM user_coupons WHERE id = ?";
        List<UserCoupon> userCoupons = jdbcTemplate.query(sql, userCouponRowMapper, id);
        return userCoupons.isEmpty() ? Optional.empty() : Optional.of(userCoupons.get(0));
    }
    
    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        String sql = "SELECT * FROM user_coupons WHERE user_id = ? ORDER BY issued_at DESC";
        return jdbcTemplate.query(sql, userCouponRowMapper, userId);
    }
    
    @Override
    public List<UserCoupon> findByUserIdAndState(Long userId, UserCouponState state) {
        String sql = "SELECT * FROM user_coupons WHERE user_id = ? AND status = ? ORDER BY issued_at DESC";
        return jdbcTemplate.query(sql, userCouponRowMapper, userId, state.name());
    }
    
    @Override
    public List<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        String sql = "SELECT * FROM user_coupons WHERE user_id = ? AND coupon_id = ?";
        return jdbcTemplate.query(sql, userCouponRowMapper, userId, couponId);
    }
    
    @Override
    public List<UserCoupon> findByCouponId(Long couponId) {
        String sql = "SELECT * FROM user_coupons WHERE coupon_id = ? ORDER BY issued_at DESC";
        return jdbcTemplate.query(sql, userCouponRowMapper, couponId);
    }
    
    
    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        String sql = "SELECT COUNT(*) FROM user_coupons WHERE user_id = ? AND coupon_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, couponId);
        return count != null && count > 0;
    }
    
    @Override
    public long countByUserId(Long userId) {
        String sql = "SELECT COUNT(*) FROM user_coupons WHERE user_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return count != null ? count : 0;
    }
    
    public long countByUserIdAndState(Long userId, UserCouponState state) {
        String sql = "SELECT COUNT(*) FROM user_coupons WHERE user_id = ? AND status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId, state.name());
        return count != null ? count : 0;
    }
    
    @Override
    public long countByCouponId(Long couponId) {
        String sql = "SELECT COUNT(*) FROM user_coupons WHERE coupon_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, couponId);
        return count != null ? count : 0;
    }
    
    @Override
    public int countByStateAndCouponId(UserCouponState state, Long couponId) {
        String sql = "SELECT COUNT(*) FROM user_coupons WHERE status = ? AND coupon_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, state.name(), couponId);
        return count != null ? count : 0;
    }
    
    @Override
    @Transactional
    public List<UserCoupon> saveAll(List<UserCoupon> userCoupons) {
        for (UserCoupon userCoupon : userCoupons) {
            save(userCoupon);
        }
        return userCoupons;
    }
    
    @Override
    public List<UserCoupon> findAvailableCouponsByUserId(Long userId) {
        return findByUserIdAndState(userId, UserCouponState.AVAILABLE);
    }
    
    @Override
    public List<UserCoupon> findExpiredCouponsByUserId(Long userId, LocalDateTime now) {
        String sql = """        
            SELECT * FROM user_coupons 
            WHERE user_id = ? AND status = 'AVAILABLE' AND expires_at < ?
            ORDER BY expires_at ASC
            """;
        return jdbcTemplate.query(sql, userCouponRowMapper, userId, Timestamp.valueOf(now));
    }
    
    @Override
    public List<UserCoupon> findExpiringCoupons(LocalDateTime expiryThreshold) {
        String sql = """        
            SELECT * FROM user_coupons 
            WHERE status = 'AVAILABLE' AND expires_at < ?
            ORDER BY expires_at ASC
            """;
        return jdbcTemplate.query(sql, userCouponRowMapper, Timestamp.valueOf(expiryThreshold));
    }
    
    @Override
    public List<UserCoupon> findByState(UserCouponState state) {
        String sql = "SELECT * FROM user_coupons WHERE status = ? ORDER BY issued_at DESC";
        return jdbcTemplate.query(sql, userCouponRowMapper, state.name());
    }
    
    @Override
    public List<UserCoupon> findAll() {
        String sql = "SELECT * FROM user_coupons ORDER BY issued_at DESC";
        return jdbcTemplate.query(sql, userCouponRowMapper);
    }
    
    @Override
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM user_coupons WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
    
    @Override
    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM user_coupons WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
    
    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        String sql = "DELETE FROM user_coupons WHERE user_id = ?";
        jdbcTemplate.update(sql, userId);
    }
    
    @Override
    @Transactional
    public void deleteByCouponId(Long couponId) {
        String sql = "DELETE FROM user_coupons WHERE coupon_id = ?";
        jdbcTemplate.update(sql, couponId);
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM user_coupons";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
    
    @Override
    public long countByState(UserCouponState state) {
        String sql = "SELECT COUNT(*) FROM user_coupons WHERE status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, state.name());
        return count != null ? count : 0;
    }
    
    @Override
    public List<UserCoupon> findByUserIdOrderByIssuedAtDesc(Long userId) {
        String sql = "SELECT * FROM user_coupons WHERE user_id = ? ORDER BY issued_at DESC";
        return jdbcTemplate.query(sql, userCouponRowMapper, userId);
    }
    
    @Override
    public List<UserCoupon> findByUserIdAndStateOrderByIssuedAtDesc(Long userId, UserCouponState state) {
        String sql = "SELECT * FROM user_coupons WHERE user_id = ? AND status = ? ORDER BY issued_at DESC";
        return jdbcTemplate.query(sql, userCouponRowMapper, userId, state.name());
    }
    
    @Override
    public List<UserCoupon> findByUserIdAndStateOrderByUsedAtDesc(Long userId, UserCouponState state) {
        String sql = "SELECT * FROM user_coupons WHERE user_id = ? AND status = ? ORDER BY used_at DESC";
        return jdbcTemplate.query(sql, userCouponRowMapper, userId, state.name());
    }
    
    @Override
    public List<UserCoupon> findExpiredAvailableCoupons(LocalDateTime now) {
        String sql = """        
            SELECT * FROM user_coupons 
            WHERE status = 'AVAILABLE' AND expires_at < ?
            ORDER BY expires_at ASC
            """;
        return jdbcTemplate.query(sql, userCouponRowMapper, Timestamp.valueOf(now));
    }
    
    private static class UserCouponRowMapper implements RowMapper<UserCoupon> {
        @Override
        public UserCoupon mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp usedAtTimestamp = rs.getTimestamp("used_at");
            LocalDateTime usedAt = usedAtTimestamp != null ? usedAtTimestamp.toLocalDateTime() : null;
            
            return UserCoupon.restore(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("coupon_id"),
                UserCouponState.valueOf(rs.getString("status")),
                rs.getTimestamp("issued_at").toLocalDateTime(),
                usedAt,
                rs.getTimestamp("expires_at").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            );
        }
    }
}