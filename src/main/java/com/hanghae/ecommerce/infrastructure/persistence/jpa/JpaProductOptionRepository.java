package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.product.ProductOption;
import com.hanghae.ecommerce.domain.product.ProductState;
import com.hanghae.ecommerce.domain.product.repository.ProductOptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaProductOptionRepository implements ProductOptionRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public JpaProductOptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public ProductOption save(ProductOption productOption) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<ProductOption> findById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<ProductOption> findByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<ProductOption> findByProductIdAndState(Long productId, ProductState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<ProductOption> findByState(ProductState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<ProductOption> findAvailableOptions() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<ProductOption> findAvailableOptionsByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<ProductOption> findAll() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public boolean existsByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public void deleteById(Long id) {
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
    public long countByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public long countByState(ProductState state) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
}