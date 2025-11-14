package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.payment.BalanceTransaction;
import com.hanghae.ecommerce.domain.payment.TransactionType;
import com.hanghae.ecommerce.domain.payment.repository.BalanceTransactionRepository;
import com.hanghae.ecommerce.domain.user.Point;
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
@Profile("!test")
public class JpaBalanceTransactionRepository implements BalanceTransactionRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final BalanceTransactionRowMapper balanceTransactionRowMapper = new BalanceTransactionRowMapper();
    
    public JpaBalanceTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    @Transactional
    public BalanceTransaction save(BalanceTransaction transaction) {
        String sql = """
            INSERT INTO balance_transactions (id, user_id, type, amount, balance_after, 
                description, reference_id, reference_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            transaction.getId(),
            transaction.getUserId(),
            transaction.getType().name(),
            transaction.getAmount().getValue(),
            transaction.getBalanceAfter().getValue(),
            transaction.getDescription(),
            transaction.getOrderId(),
            "ORDER"
        );
        
        return transaction;
    }
    
    @Override
    public Optional<BalanceTransaction> findById(Long id) {
        String sql = "SELECT * FROM balance_transactions WHERE id = ?";
        List<BalanceTransaction> transactions = jdbcTemplate.query(sql, balanceTransactionRowMapper, id);
        return transactions.isEmpty() ? Optional.empty() : Optional.of(transactions.get(0));
    }
    
    @Override
    public List<BalanceTransaction> findByUserId(Long userId) {
        String sql = "SELECT * FROM balance_transactions WHERE user_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, balanceTransactionRowMapper, userId);
    }
    
    @Override
    public List<BalanceTransaction> findByUserIdAndType(Long userId, TransactionType type) {
        String sql = "SELECT * FROM balance_transactions WHERE user_id = ? AND type = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, balanceTransactionRowMapper, userId, type.name());
    }
    
    @Override
    public List<BalanceTransaction> findByOrderId(Long orderId) {
        String sql = "SELECT * FROM balance_transactions WHERE reference_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, balanceTransactionRowMapper, orderId);
    }
    
    @Override
    public List<BalanceTransaction> findByType(TransactionType type) {
        String sql = "SELECT * FROM balance_transactions WHERE type = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, balanceTransactionRowMapper, type.name());
    }
    
    @Override
    public List<BalanceTransaction> findChargeTransactions() {
        return findByType(TransactionType.CHARGE);
    }
    
    @Override
    public List<BalanceTransaction> findPaymentTransactions() {
        return findByType(TransactionType.PAYMENT);
    }
    
    @Override
    public List<BalanceTransaction> findRefundTransactions() {
        return findByType(TransactionType.REFUND);
    }
    
    @Override
    public List<BalanceTransaction> findOrderRelatedTransactions() {
        String sql = "SELECT * FROM balance_transactions WHERE reference_id IS NOT NULL ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, balanceTransactionRowMapper);
    }
    
    @Override
    public List<BalanceTransaction> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT * FROM balance_transactions WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, balanceTransactionRowMapper, Timestamp.valueOf(startDate), Timestamp.valueOf(endDate));
    }
    
    @Override
    public List<BalanceTransaction> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT * FROM balance_transactions WHERE user_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, balanceTransactionRowMapper, userId, Timestamp.valueOf(startDate), Timestamp.valueOf(endDate));
    }
    
    @Override
    public List<BalanceTransaction> findAll() {
        String sql = "SELECT * FROM balance_transactions ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, balanceTransactionRowMapper);
    }
    
    @Override
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM balance_transactions WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
    
    @Override
    public boolean existsByUserId(Long userId) {
        String sql = "SELECT COUNT(*) FROM balance_transactions WHERE user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count != null && count > 0;
    }
    
    @Override
    public boolean existsByOrderId(Long orderId) {
        String sql = "SELECT COUNT(*) FROM balance_transactions WHERE reference_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, orderId);
        return count != null && count > 0;
    }
    
    @Override
    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM balance_transactions WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
    
    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        String sql = "DELETE FROM balance_transactions WHERE user_id = ?";
        jdbcTemplate.update(sql, userId);
    }
    
    @Override
    @Transactional
    public void deleteByOrderId(Long orderId) {
        String sql = "DELETE FROM balance_transactions WHERE reference_id = ?";
        jdbcTemplate.update(sql, orderId);
    }
    
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM balance_transactions";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
    
    @Override
    public long countByUserId(Long userId) {
        String sql = "SELECT COUNT(*) FROM balance_transactions WHERE user_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return count != null ? count : 0;
    }
    
    @Override
    public long countByOrderId(Long orderId) {
        String sql = "SELECT COUNT(*) FROM balance_transactions WHERE reference_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, orderId);
        return count != null ? count : 0;
    }
    
    @Override
    public long countByType(TransactionType type) {
        String sql = "SELECT COUNT(*) FROM balance_transactions WHERE type = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, type.name());
        return count != null ? count : 0;
    }
    
    @Override
    public long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT COUNT(*) FROM balance_transactions WHERE created_at BETWEEN ? AND ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.valueOf(startDate), Timestamp.valueOf(endDate));
        return count != null ? count : 0;
    }
    
    private static class BalanceTransactionRowMapper implements RowMapper<BalanceTransaction> {
        @Override
        public BalanceTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
            return BalanceTransaction.restore(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getObject("reference_id", Long.class), // orderId (nullable)
                TransactionType.valueOf(rs.getString("type")),
                Point.of(rs.getInt("amount")),
                Point.of(rs.getInt("amount") - rs.getInt("balance_after") + rs.getInt("amount")), // balanceBefore
                Point.of(rs.getInt("balance_after")),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            );
        }
    }
}