package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.payment.PaymentState;
import com.hanghae.ecommerce.domain.payment.repository.PaymentRepository;
import com.hanghae.ecommerce.domain.product.Money;
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
public class JpaPaymentRepository implements PaymentRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final PaymentRowMapper paymentRowMapper = new PaymentRowMapper();
    
    public JpaPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    @Transactional
    public Payment save(Payment payment) {
        String sql = """
            INSERT INTO payments (id, order_id, payment_method, amount, status, paid_at, failed_at, failed_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                paid_at = VALUES(paid_at),
                failed_at = VALUES(failed_at),
                failed_reason = VALUES(failed_reason)
            """;
        
        jdbcTemplate.update(sql,
            payment.getId(),
            payment.getOrderId(),
            payment.getMethod().name(),
            payment.getPaidAmount().getValue(),
            payment.getState().name(),
            payment.getPaidAt() != null ? Timestamp.valueOf(payment.getPaidAt()) : null,
            null, // failed_at - Payment 도메인에 없음
            null  // failed_reason - Payment 도메인에 없음
        );
        
        return payment;
    }

    @Override
    public Optional<Payment> findById(Long id) {
        String sql = "SELECT * FROM payments WHERE id = ?";
        List<Payment> payments = jdbcTemplate.query(sql, paymentRowMapper, id);
        return payments.isEmpty() ? Optional.empty() : Optional.of(payments.get(0));
    }

    @Override
    public List<Payment> findByOrderId(Long orderId) {
        String sql = "SELECT * FROM payments WHERE order_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, paymentRowMapper, orderId);
    }

    @Override
    public List<Payment> findByOrderIdAndState(Long orderId, PaymentState state) {
        String sql = "SELECT * FROM payments WHERE order_id = ? AND status = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, paymentRowMapper, orderId, state.name());
    }
    
    @Override
    public List<Payment> findByState(PaymentState state) {
        String sql = "SELECT * FROM payments WHERE status = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, paymentRowMapper, state.name());
    }

    @Override
    public List<Payment> findByMethod(PaymentMethod method) {
        String sql = "SELECT * FROM payments WHERE payment_method = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, paymentRowMapper, method.name());
    }

    @Override
    public List<Payment> findPendingPayments() {
        return findByState(PaymentState.PENDING);
    }

    @Override
    public List<Payment> findCompletedPayments() {
        return findByState(PaymentState.COMPLETED);
    }

    @Override
    public List<Payment> findFailedPayments() {
        return findByState(PaymentState.FAILED);
    }

    @Override
    public List<Payment> findExpiredPayments(LocalDateTime now) {
        String sql = "SELECT * FROM payments WHERE expires_at < ? AND status = 'PENDING' ORDER BY expires_at DESC";
        return jdbcTemplate.query(sql, paymentRowMapper, Timestamp.valueOf(now));
    }

    @Override
    public List<Payment> findExpiringPayments(LocalDateTime expiryThreshold) {
        String sql = "SELECT * FROM payments WHERE expires_at < ? AND status = 'PENDING' ORDER BY expires_at ASC";
        return jdbcTemplate.query(sql, paymentRowMapper, Timestamp.valueOf(expiryThreshold));
    }

    @Override
    public List<Payment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT * FROM payments WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, paymentRowMapper, Timestamp.valueOf(startDate), Timestamp.valueOf(endDate));
    }

    @Override
    public List<Payment> findAll() {
        String sql = "SELECT * FROM payments ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, paymentRowMapper);
    }

    @Override
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM payments WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByOrderId(Long orderId) {
        String sql = "SELECT COUNT(*) FROM payments WHERE order_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, orderId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM payments WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    @Transactional
    public void deleteByOrderId(Long orderId) {
        String sql = "DELETE FROM payments WHERE order_id = ?";
        jdbcTemplate.update(sql, orderId);
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM payments";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByOrderId(Long orderId) {
        String sql = "SELECT COUNT(*) FROM payments WHERE order_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, orderId);
        return count != null ? count : 0;
    }

    @Override
    public long countByState(PaymentState state) {
        String sql = "SELECT COUNT(*) FROM payments WHERE status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, state.name());
        return count != null ? count : 0;
    }

    @Override
    public long countByMethod(PaymentMethod method) {
        String sql = "SELECT COUNT(*) FROM payments WHERE payment_method = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, method.name());
        return count != null ? count : 0;
    }

    @Override
    public long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT COUNT(*) FROM payments WHERE created_at BETWEEN ? AND ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.valueOf(startDate), Timestamp.valueOf(endDate));
        return count != null ? count : 0;
    }

    @Override
    public boolean existsByOrderIdAndState(Long orderId, PaymentState state) {
        String sql = "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, orderId, state.name());
        return count != null && count > 0;
    }

    
    private static class PaymentRowMapper implements RowMapper<Payment> {
        @Override
        public Payment mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp paidAtTimestamp = rs.getTimestamp("paid_at");
            LocalDateTime paidAt = paidAtTimestamp != null ? paidAtTimestamp.toLocalDateTime() : null;
            
            Timestamp expiresAtTimestamp = rs.getTimestamp("expires_at");
            LocalDateTime expiresAt = expiresAtTimestamp != null ? expiresAtTimestamp.toLocalDateTime() : null;
            
            return Payment.restore(
                rs.getLong("id"),
                rs.getLong("order_id"),
                PaymentState.valueOf(rs.getString("status")),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                Money.of(rs.getInt("amount")),
                paidAt,
                expiresAt,
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            );
        }
    }
}