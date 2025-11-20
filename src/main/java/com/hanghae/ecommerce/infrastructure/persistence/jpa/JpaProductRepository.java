package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.ProductState;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaProductRepository implements ProductRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ProductRowMapper productRowMapper = new ProductRowMapper();

    public JpaProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Product save(Product product) {
        if (product.getId() == null) {
            String sql = "INSERT INTO products (name, description, price, limited_quantity, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())";
            org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement ps = connection.prepareStatement(sql,
                        java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, product.getName());
                ps.setString(2, product.getDescription());
                ps.setInt(3, product.getPrice().getValue());
                ps.setInt(4, product.getLimitedQuantity().getValue());
                ps.setString(5, product.getState().name());
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key == null) {
                throw new RuntimeException("Failed to insert product, no ID obtained.");
            }

            return Product.restore(
                    key.longValue(),
                    product.getState(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getLimitedQuantity(),
                    LocalDateTime.now(), // created_at (approx)
                    LocalDateTime.now() // updated_at (approx)
            );
        } else {
            String sql = "UPDATE products SET name=?, description=?, price=?, limited_quantity=?, status=?, updated_at=NOW() WHERE id=?";
            jdbcTemplate.update(sql,
                    product.getName(),
                    product.getDescription(),
                    product.getPrice().getValue(),
                    product.getLimitedQuantity().getValue(),
                    product.getState().name(),
                    product.getId());
            return product;
        }
    }

    @Override
    public Optional<Product> findById(Long id) {
        String sql = "SELECT * FROM products WHERE id = ?";
        List<Product> products = jdbcTemplate.query(sql, productRowMapper, id);
        return products.isEmpty() ? Optional.empty() : Optional.of(products.get(0));
    }

    @Override
    public List<Product> findByIdIn(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toArray(String[]::new));
        String sql = "SELECT * FROM products WHERE id IN (" + placeholders + ") ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, productRowMapper, ids.toArray());
    }

    @Override
    public List<Product> findByNameContaining(String name) {
        String sql = "SELECT * FROM products WHERE name LIKE ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, productRowMapper, "%" + name + "%");
    }

    @Override
    public List<Product> findAll() {
        String sql = "SELECT * FROM products ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, productRowMapper);
    }

    @Override
    public List<Product> findByState(ProductState state) {
        String sql = "SELECT * FROM products WHERE status = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, productRowMapper, state.name());
    }

    @Override
    public List<Product> findAvailableProducts() {
        return findByState(ProductState.NORMAL);
    }

    @Override
    public List<Product> findByStateIsAvailable() {
        return findAvailableProducts();
    }

    @Override
    public List<Product> findByNameContainingAndStateIsAvailable(String keyword) {
        String sql = "SELECT * FROM products WHERE name LIKE ? AND status = 'NORMAL' ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, productRowMapper, "%" + keyword + "%");
    }

    @Override
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM products WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM products WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM products WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM products";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByState(ProductState state) {
        String sql = "SELECT COUNT(*) FROM products WHERE status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, state.name());
        return count != null ? count : 0;
    }

    private static class ProductRowMapper implements RowMapper<Product> {
        @Override
        public Product mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Product.restore(
                    rs.getLong("id"),
                    ProductState.valueOf(rs.getString("status")),
                    rs.getString("name"),
                    rs.getString("description"),
                    Money.of(rs.getInt("price")),
                    Quantity.of(rs.getInt("limited_quantity")),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("updated_at").toLocalDateTime());
        }
    }

    @Override
    @Transactional
    public boolean decreaseStock(Long productId, Quantity quantity) {
        String sql = """
                UPDATE products
                SET limited_quantity = limited_quantity - ?
                WHERE id = ? AND limited_quantity >= ?
                """;

        int affectedRows = jdbcTemplate.update(sql,
                quantity.getValue(),
                productId,
                quantity.getValue());

        return affectedRows > 0;
    }
}