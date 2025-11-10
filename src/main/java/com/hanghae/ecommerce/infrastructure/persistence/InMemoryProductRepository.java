package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.ProductState;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 상품 Repository 구현체
 */
@Repository
public class InMemoryProductRepository implements ProductRepository {
    
    private final ConcurrentHashMap<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public Product save(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("상품 정보는 null일 수 없습니다.");
        }
        
        Product savedProduct;
        if (product.getId() == null) {
            // 새로운 상품 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedProduct = Product.restore(
                newId,
                product.getState(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getLimitedQuantity(),
                product.getCreatedAt(),
                now
            );
        } else {
            // 기존 상품 업데이트
            savedProduct = Product.restore(
                product.getId(),
                product.getState(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getLimitedQuantity(),
                product.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedProduct.getId(), savedProduct);
        return savedProduct;
    }
    
    @Override
    public Optional<Product> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public List<Product> findByIdIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(product -> ids.contains(product.getId()))
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Product> findByNameContaining(String name) {
        if (name == null || name.trim().isEmpty()) {
            return List.of();
        }
        
        String searchName = name.toLowerCase();
        return store.values().stream()
            .filter(product -> product.getName().toLowerCase().contains(searchName))
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Product> findByState(ProductState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(product -> state.equals(product.getState()))
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Product> findAvailableProducts() {
        return store.values().stream()
            .filter(Product::isAvailable)
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Product> findAll() {
        return store.values().stream()
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public boolean existsById(Long id) {
        if (id == null) {
            return false;
        }
        return store.containsKey(id);
    }
    
    @Override
    public boolean existsByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(product -> name.equals(product.getName()));
    }
    
    @Override
    public void deleteById(Long id) {
        if (id != null) {
            store.remove(id);
        }
    }
    
    @Override
    public long count() {
        return store.size();
    }
    
    @Override
    public long countByState(ProductState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(product -> state.equals(product.getState()))
            .count();
    }
    
    @Override
    public List<Product> findByStateIsAvailable() {
        return findAvailableProducts();
    }
    
    @Override
    public List<Product> findByNameContainingAndStateIsAvailable(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        
        String searchKeyword = keyword.toLowerCase();
        return store.values().stream()
            .filter(product -> product.isAvailable() && 
                             product.getName().toLowerCase().contains(searchKeyword))
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
}