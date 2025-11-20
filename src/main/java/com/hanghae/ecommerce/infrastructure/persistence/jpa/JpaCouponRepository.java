package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.CouponState;
import com.hanghae.ecommerce.domain.coupon.DiscountPolicy;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Quantity;
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
public class JpaCouponRepository implements CouponRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final CouponRowMapper couponRowMapper = new CouponRowMapper();
    
    public JpaCouponRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    @Transactional
    public Coupon save(Coupon coupon) {
        String sql = """
            INSERT INTO coupons (id, name, discount_type, discount_value, min_order_amount, 
                max_discount_amount, total_quantity, issued_quantity, start_date, end_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                discount_type = VALUES(discount_type),
                discount_value = VALUES(discount_value),
                min_order_amount = VALUES(min_order_amount),
                max_discount_amount = VALUES(max_discount_amount),
                total_quantity = VALUES(total_quantity),
                issued_quantity = VALUES(issued_quantity),
                start_date = VALUES(start_date),
                end_date = VALUES(end_date)
            """;
        
        jdbcTemplate.update(sql,
            coupon.getId(),
            coupon.getName(),
            coupon.getDiscountPolicy().getDiscountType().name(),
            coupon.getDiscountPolicy().getDiscountValue(),
            coupon.getDiscountPolicy().getMinOrderAmount() != null ? 
                coupon.getDiscountPolicy().getMinOrderAmount().getValue() : null,
            null, // max_discount_amount는 현재 도메인에 없음
            coupon.getTotalQuantity().getValue(),
            coupon.getIssuedQuantity().getValue(),
            Timestamp.valueOf(coupon.getBeginDate()),
            Timestamp.valueOf(coupon.getEndDate())
        );
        
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        String sql = "SELECT * FROM coupons WHERE id = ?";
        List<Coupon> coupons = jdbcTemplate.query(sql, couponRowMapper, id);
        return coupons.isEmpty() ? Optional.empty() : Optional.of(coupons.get(0));
    }

    @Override
    public List<Coupon> findByNameContaining(String name) {
        String sql = "SELECT * FROM coupons WHERE name LIKE ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, couponRowMapper, "%" + name + "%");
    }
    
    @Override
    public Optional<Coupon> findById(String id) {
        return findById(Long.valueOf(id));
    }
    
    @Override
    public List<Coupon> findAll() {
        String sql = "SELECT * FROM coupons ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, couponRowMapper);
    }

    @Override
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM coupons WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM coupons WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM coupons WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM coupons";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByState(CouponState state) {
        // 상태별 동적 쿼리
        String sql;
        switch (state) {
            case NORMAL:
                sql = "SELECT COUNT(*) FROM coupons WHERE issued_quantity < total_quantity AND NOW() BETWEEN start_date AND end_date";
                break;
            case DISCONTINUED:
                sql = "SELECT COUNT(*) FROM coupons WHERE issued_quantity >= total_quantity";
                break;
            case EXPIRED:
                sql = "SELECT COUNT(*) FROM coupons WHERE NOW() > end_date";
                break;
            default:
                sql = "SELECT COUNT(*) FROM coupons";
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public Optional<Coupon> findByIdForUpdate(Long id) {
        String sql = "SELECT * FROM coupons WHERE id = ? FOR UPDATE";
        List<Coupon> coupons = jdbcTemplate.query(sql, couponRowMapper, id);
        return coupons.isEmpty() ? Optional.empty() : Optional.of(coupons.get(0));
    }

    @Override
    public List<Coupon> findAvailableCoupons(LocalDateTime now) {
        String sql = """
            SELECT * FROM coupons 
            WHERE issued_quantity < total_quantity
            AND ? BETWEEN start_date AND end_date
            ORDER BY end_date ASC
            """;
        return jdbcTemplate.query(sql, couponRowMapper, Timestamp.valueOf(now));
    }
    
    @Override
    public List<Coupon> findByState(CouponState state) {
        // CouponState에 따라 조건 분기
        String sql;
        Object[] params;
        
        switch (state) {
            case NORMAL:
                sql = """
                    SELECT * FROM coupons 
                    WHERE issued_quantity < total_quantity
                    AND NOW() BETWEEN start_date AND end_date
                    ORDER BY created_at DESC
                    """;
                params = new Object[0];
                break;
            case DISCONTINUED:
                sql = """
                    SELECT * FROM coupons 
                    WHERE issued_quantity >= total_quantity
                    ORDER BY created_at DESC
                    """;
                params = new Object[0];
                break;
            case EXPIRED:
                sql = """
                    SELECT * FROM coupons 
                    WHERE NOW() > end_date
                    ORDER BY created_at DESC
                    """;
                params = new Object[0];
                break;
            default:
                sql = "SELECT * FROM coupons ORDER BY created_at DESC";
                params = new Object[0];
        }
        
        return jdbcTemplate.query(sql, couponRowMapper, params);
    }

    @Override
    public List<Coupon> findIssuableCoupons() {
        return findAvailableCoupons(LocalDateTime.now());
    }

    @Override
    public List<Coupon> findValidCoupons(LocalDateTime now) {
        String sql = "SELECT * FROM coupons WHERE ? BETWEEN start_date AND end_date ORDER BY end_date ASC";
        return jdbcTemplate.query(sql, couponRowMapper, Timestamp.valueOf(now));
    }

    @Override
    public List<Coupon> findExpiredCoupons(LocalDateTime now) {
        String sql = "SELECT * FROM coupons WHERE ? > end_date ORDER BY end_date DESC";
        return jdbcTemplate.query(sql, couponRowMapper, Timestamp.valueOf(now));
    }

    @Override
    public List<Coupon> findCouponsWithRemainingQuantity() {
        String sql = "SELECT * FROM coupons WHERE issued_quantity < total_quantity ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, couponRowMapper);
    }

    @Transactional
    public void delete(Coupon coupon) {
        String sql = "DELETE FROM coupons WHERE id = ?";
        jdbcTemplate.update(sql, coupon.getId());
    }
    
    @Transactional
    public void deleteAll() {
        jdbcTemplate.execute("DELETE FROM coupons");
    }
    
    @Transactional
    public boolean increaseIssuedQuantity(Long couponId) {
        String sql = """
            UPDATE coupons 
            SET issued_quantity = issued_quantity + 1
            WHERE id = ? AND issued_quantity < total_quantity
            """;
        
        int updatedRows = jdbcTemplate.update(sql, couponId);
        return updatedRows > 0;
    }
    
    private static class CouponRowMapper implements RowMapper<Coupon> {
        @Override
        public Coupon mapRow(ResultSet rs, int rowNum) throws SQLException {
            // DiscountPolicy 복원
            DiscountPolicy discountPolicy;
            String discountType = rs.getString("discount_type");
            int discountValue = rs.getInt("discount_value");
            
            if ("RATE".equals(discountType)) {
                discountPolicy = DiscountPolicy.rate(discountValue);
            } else {
                discountPolicy = DiscountPolicy.amount(Money.of(discountValue));
            }
            
            return Coupon.restore(
                rs.getLong("id"),
                rs.getString("name"),
                CouponState.valueOf(rs.getString("status")),
                discountPolicy,
                Quantity.of(rs.getInt("total_quantity")),
                Quantity.of(rs.getInt("issued_quantity")),
                rs.getTimestamp("start_date").toLocalDateTime(),
                rs.getTimestamp("end_date").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            );
        }
    }
}