package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaStockRepository implements StockRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public JpaStockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public Stock save(Stock stock) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<Stock> findById(Long id) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<Stock> findByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<Stock> findByProductIdAndProductOptionId(Long productId, Long productOptionId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Stock> findAllByProductId(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Stock> findByProductOptionIdIsNotNull() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Stock> findByProductOptionIdIsNull() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Stock> findLowStockItems(int threshold) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Stock> findOutOfStockItems() {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Stock> findAll() {
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
    public boolean existsByProductIdAndProductOptionId(Long productId, Long productOptionId) {
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
    public Optional<Stock> findByProductIdAndProductOptionIdIsNull(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public List<Stock> findByProductIdInAndProductOptionIdIsNull(List<Long> productIds) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
    
    @Override
    public Optional<Stock> findByProductIdAndProductOptionIdIsNullForUpdate(Long productId) {
        throw new UnsupportedOperationException("JPA Repository implementation needed");
    }
}