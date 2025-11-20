package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaStockRepository implements StockRepository {

    private final JdbcTemplate jdbcTemplate;
    private final StockRowMapper stockRowMapper = new StockRowMapper();

    public JpaStockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Stock save(Stock stock) {
        if (stock.getId() == null) {
            String sql = "INSERT INTO stocks (product_id, product_option_id, available_quantity, sold_quantity, memo, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())";
            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, stock.getProductId());
                if (stock.getProductOptionId() != null) {
                    ps.setLong(2, stock.getProductOptionId());
                } else {
                    ps.setNull(2, Types.BIGINT);
                }
                ps.setInt(3, stock.getAvailableQuantity().getValue());
                ps.setInt(4, stock.getSoldQuantity().getValue());
                ps.setString(5, stock.getMemo());
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key == null) {
                throw new RuntimeException("Failed to insert stock, no ID obtained.");
            }

            return Stock.restore(
                    key.longValue(),
                    stock.getProductId(),
                    stock.getProductOptionId(),
                    stock.getAvailableQuantity(),
                    stock.getSoldQuantity(),
                    stock.getMemo(),
                    LocalDateTime.now(),
                    LocalDateTime.now());
        } else {
            String sql = "UPDATE stocks SET available_quantity=?, sold_quantity=?, memo=?, updated_at=NOW() WHERE id=?";
            jdbcTemplate.update(sql,
                    stock.getAvailableQuantity().getValue(),
                    stock.getSoldQuantity().getValue(),
                    stock.getMemo(),
                    stock.getId());
            return stock;
        }
    }

    @Override
    public Optional<Stock> findById(Long id) {
        String sql = "SELECT * FROM stocks WHERE id = ?";
        List<Stock> stocks = jdbcTemplate.query(sql, stockRowMapper, id);
        return stocks.isEmpty() ? Optional.empty() : Optional.of(stocks.get(0));
    }

    @Override
    public Optional<Stock> findByProductId(Long productId) {
        String sql = "SELECT * FROM stocks WHERE product_id = ? AND product_option_id IS NULL";
        List<Stock> stocks = jdbcTemplate.query(sql, stockRowMapper, productId);
        return stocks.isEmpty() ? Optional.empty() : Optional.of(stocks.get(0));
    }

    @Override
    public Optional<Stock> findByProductIdAndProductOptionId(Long productId, Long productOptionId) {
        String sql = "SELECT * FROM stocks WHERE product_id = ? AND product_option_id = ?";
        List<Stock> stocks = jdbcTemplate.query(sql, stockRowMapper, productId, productOptionId);
        return stocks.isEmpty() ? Optional.empty() : Optional.of(stocks.get(0));
    }

    @Override
    public List<Stock> findAllByProductId(Long productId) {
        String sql = "SELECT * FROM stocks WHERE product_id = ?";
        return jdbcTemplate.query(sql, stockRowMapper, productId);
    }

    @Override
    public List<Stock> findByProductOptionIdIsNotNull() {
        String sql = "SELECT * FROM stocks WHERE product_option_id IS NOT NULL";
        return jdbcTemplate.query(sql, stockRowMapper);
    }

    @Override
    public List<Stock> findByProductOptionIdIsNull() {
        String sql = "SELECT * FROM stocks WHERE product_option_id IS NULL";
        return jdbcTemplate.query(sql, stockRowMapper);
    }

    @Override
    public List<Stock> findLowStockItems(int threshold) {
        String sql = "SELECT * FROM stocks WHERE available_quantity <= ?";
        return jdbcTemplate.query(sql, stockRowMapper, threshold);
    }

    @Override
    public List<Stock> findOutOfStockItems() {
        String sql = "SELECT * FROM stocks WHERE available_quantity = 0";
        return jdbcTemplate.query(sql, stockRowMapper);
    }

    @Override
    public List<Stock> findAll() {
        String sql = "SELECT * FROM stocks";
        return jdbcTemplate.query(sql, stockRowMapper);
    }

    @Override
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM stocks WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByProductId(Long productId) {
        String sql = "SELECT COUNT(*) FROM stocks WHERE product_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, productId);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByProductIdAndProductOptionId(Long productId, Long productOptionId) {
        String sql = "SELECT COUNT(*) FROM stocks WHERE product_id = ? AND product_option_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, productId, productOptionId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM stocks WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    @Transactional
    public void deleteByProductId(Long productId) {
        String sql = "DELETE FROM stocks WHERE product_id = ?";
        jdbcTemplate.update(sql, productId);
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM stocks";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByProductId(Long productId) {
        String sql = "SELECT COUNT(*) FROM stocks WHERE product_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, productId);
        return count != null ? count : 0;
    }

    @Override
    public Optional<Stock> findByProductIdAndProductOptionIdIsNull(Long productId) {
        return findByProductId(productId);
    }

    @Override
    public List<Stock> findByProductIdInAndProductOptionIdIsNull(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", productIds.stream().map(id -> "?").toArray(String[]::new));
        String sql = "SELECT * FROM stocks WHERE product_id IN (" + placeholders + ") AND product_option_id IS NULL";
        return jdbcTemplate.query(sql, stockRowMapper, productIds.toArray());
    }

    @Override
    public Optional<Stock> findByProductIdAndProductOptionIdIsNullForUpdate(Long productId) {
        String sql = "SELECT * FROM stocks WHERE product_id = ? AND product_option_id IS NULL FOR UPDATE";
        List<Stock> stocks = jdbcTemplate.query(sql, stockRowMapper, productId);
        return stocks.isEmpty() ? Optional.empty() : Optional.of(stocks.get(0));
    }

    private static class StockRowMapper implements RowMapper<Stock> {
        @Override
        public Stock mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long productOptionId = rs.getLong("product_option_id");
            if (rs.wasNull()) {
                productOptionId = null;
            }
            return Stock.restore(
                    rs.getLong("id"),
                    rs.getLong("product_id"),
                    productOptionId,
                    Quantity.of(rs.getInt("available_quantity")),
                    Quantity.of(rs.getInt("sold_quantity")),
                    rs.getString("memo"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("updated_at").toLocalDateTime());
        }
    }
}