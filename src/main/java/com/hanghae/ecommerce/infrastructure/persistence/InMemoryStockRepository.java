package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 재고 Repository 구현체
 */
@Repository
public class InMemoryStockRepository implements StockRepository {
    
    private final ConcurrentHashMap<Long, Stock> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public Stock save(Stock stock) {
        if (stock == null) {
            throw new IllegalArgumentException("재고 정보는 null일 수 없습니다.");
        }
        
        Stock savedStock;
        if (stock.getId() == null) {
            // 새로운 재고 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedStock = Stock.restore(
                newId,
                stock.getProductId(),
                stock.getProductOptionId(),
                stock.getAvailableQuantity(),
                stock.getSoldQuantity(),
                stock.getMemo(),
                stock.getCreatedAt(),
                now
            );
        } else {
            // 기존 재고 업데이트
            savedStock = Stock.restore(
                stock.getId(),
                stock.getProductId(),
                stock.getProductOptionId(),
                stock.getAvailableQuantity(),
                stock.getSoldQuantity(),
                stock.getMemo(),
                stock.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedStock.getId(), savedStock);
        return savedStock;
    }
    
    @Override
    public Optional<Stock> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public Optional<Stock> findByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(stock -> productId.equals(stock.getProductId()) && stock.getProductOptionId() == null)
            .findFirst();
    }
    
    @Override
    public Optional<Stock> findByProductIdAndProductOptionId(Long productId, Long productOptionId) {
        if (productId == null || productOptionId == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(stock -> productId.equals(stock.getProductId()) && 
                           productOptionId.equals(stock.getProductOptionId()))
            .findFirst();
    }
    
    @Override
    public List<Stock> findAllByProductId(Long productId) {
        if (productId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(stock -> productId.equals(stock.getProductId()))
            .sorted((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Stock> findByProductOptionIdIsNotNull() {
        return store.values().stream()
            .filter(stock -> stock.getProductOptionId() != null)
            .sorted((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Stock> findByProductOptionIdIsNull() {
        return store.values().stream()
            .filter(stock -> stock.getProductOptionId() == null)
            .sorted((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Stock> findLowStockItems(int threshold) {
        if (threshold < 0) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(stock -> stock.getAvailableQuantity().getValue() <= threshold && !stock.isEmpty())
            .sorted((s1, s2) -> Integer.compare(s1.getAvailableQuantity().getValue(), 
                                              s2.getAvailableQuantity().getValue())) // 재고 부족 순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Stock> findOutOfStockItems() {
        return store.values().stream()
            .filter(Stock::isEmpty)
            .sorted((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Stock> findAll() {
        return store.values().stream()
            .sorted((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt())) // 최신순
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
            .anyMatch(stock -> productId.equals(stock.getProductId()));
    }
    
    @Override
    public boolean existsByProductIdAndProductOptionId(Long productId, Long productOptionId) {
        if (productId == null || productOptionId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(stock -> productId.equals(stock.getProductId()) && 
                             productOptionId.equals(stock.getProductOptionId()));
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
            .filter(stock -> productId.equals(stock.getProductId()))
            .map(Stock::getId)
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
            .filter(stock -> productId.equals(stock.getProductId()))
            .count();
    }
    
    @Override
    public Optional<Stock> findByProductIdAndProductOptionIdIsNull(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(stock -> productId.equals(stock.getProductId()) && stock.getProductOptionId() == null)
            .findFirst();
    }
    
    @Override
    public List<Stock> findByProductIdInAndProductOptionIdIsNull(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(stock -> productIds.contains(stock.getProductId()) && stock.getProductOptionId() == null)
            .sorted((s1, s2) -> s2.getCreatedAt().compareTo(s1.getCreatedAt()))
            .collect(Collectors.toList());
    }
    
    @Override
    public Optional<Stock> findByProductIdAndProductOptionIdIsNullForUpdate(Long productId) {
        // 인메모리 구현에서는 락을 시뮬레이션할 수 없으므로 일반 조회와 동일하게 처리
        return findByProductIdAndProductOptionIdIsNull(productId);
    }
}