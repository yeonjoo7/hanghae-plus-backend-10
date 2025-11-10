package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.product.ProductOption;
import com.hanghae.ecommerce.domain.product.ProductState;
import com.hanghae.ecommerce.domain.product.repository.ProductOptionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 상품 옵션 Repository 구현체
 */
@Repository
public class InMemoryProductOptionRepository implements ProductOptionRepository {
    
    private final ConcurrentHashMap<Long, ProductOption> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public ProductOption save(ProductOption productOption) {
        if (productOption == null) {
            throw new IllegalArgumentException("상품 옵션 정보는 null일 수 없습니다.");
        }
        
        ProductOption savedOption;
        if (productOption.getId() == null) {
            // 새로운 상품 옵션 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedOption = ProductOption.restore(
                newId,
                productOption.getProductId(),
                productOption.getState(),
                productOption.getPrice(),
                productOption.getCreatedAt(),
                now
            );
        } else {
            // 기존 상품 옵션 업데이트
            savedOption = ProductOption.restore(
                productOption.getId(),
                productOption.getProductId(),
                productOption.getState(),
                productOption.getPrice(),
                productOption.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedOption.getId(), savedOption);
        return savedOption;
    }
    
    @Override
    public Optional<ProductOption> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public List<ProductOption> findByProductId(Long productId) {
        if (productId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(option -> productId.equals(option.getProductId()))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ProductOption> findByProductIdAndState(Long productId, ProductState state) {
        if (productId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(option -> productId.equals(option.getProductId()) && state.equals(option.getState()))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ProductOption> findByState(ProductState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(option -> state.equals(option.getState()))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ProductOption> findAvailableOptions() {
        return store.values().stream()
            .filter(ProductOption::isAvailable)
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ProductOption> findAvailableOptionsByProductId(Long productId) {
        if (productId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(option -> productId.equals(option.getProductId()) && option.isAvailable())
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ProductOption> findAll() {
        return store.values().stream()
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
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
    public boolean existsByProductId(Long productId) {
        if (productId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(option -> productId.equals(option.getProductId()));
    }
    
    @Override
    public void deleteById(Long id) {
        if (id != null) {
            store.remove(id);
        }
    }
    
    @Override
    public void deleteByProductId(Long productId) {
        if (productId == null) {
            return;
        }
        
        List<Long> idsToDelete = store.values().stream()
            .filter(option -> productId.equals(option.getProductId()))
            .map(ProductOption::getId)
            .collect(Collectors.toList());
        
        idsToDelete.forEach(store::remove);
    }
    
    @Override
    public long count() {
        return store.size();
    }
    
    @Override
    public long countByProductId(Long productId) {
        if (productId == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(option -> productId.equals(option.getProductId()))
            .count();
    }
    
    @Override
    public long countByState(ProductState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(option -> state.equals(option.getState()))
            .count();
    }
}