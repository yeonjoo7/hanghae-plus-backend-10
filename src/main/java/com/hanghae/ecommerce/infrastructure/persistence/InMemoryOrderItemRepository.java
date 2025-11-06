package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.order.OrderItem;
import com.hanghae.ecommerce.domain.order.OrderState;
import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 주문 아이템 Repository 구현체
 */
@Repository
public class InMemoryOrderItemRepository implements OrderItemRepository {
    
    private final ConcurrentHashMap<Long, OrderItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public OrderItem save(OrderItem orderItem) {
        if (orderItem == null) {
            throw new IllegalArgumentException("주문 아이템 정보는 null일 수 없습니다.");
        }
        
        OrderItem savedOrderItem;
        if (orderItem.getId() == null) {
            // 새로운 주문 아이템 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedOrderItem = OrderItem.restore(
                newId,
                orderItem.getOrderId(),
                orderItem.getProductId(),
                orderItem.getProductOptionId(),
                orderItem.getState(),
                orderItem.getPrice(),
                orderItem.getQuantity(),
                orderItem.getDiscountAmount(),
                orderItem.getTotalAmount(),
                orderItem.getCreatedAt(),
                now
            );
        } else {
            // 기존 주문 아이템 업데이트
            savedOrderItem = OrderItem.restore(
                orderItem.getId(),
                orderItem.getOrderId(),
                orderItem.getProductId(),
                orderItem.getProductOptionId(),
                orderItem.getState(),
                orderItem.getPrice(),
                orderItem.getQuantity(),
                orderItem.getDiscountAmount(),
                orderItem.getTotalAmount(),
                orderItem.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedOrderItem.getId(), savedOrderItem);
        return savedOrderItem;
    }
    
    @Override
    public Optional<OrderItem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> orderId.equals(item.getOrderId()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<OrderItem> findByOrderIdAndState(Long orderId, OrderState state) {
        if (orderId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> orderId.equals(item.getOrderId()) && state.equals(item.getState()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<OrderItem> findByProductId(Long productId) {
        if (productId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> productId.equals(item.getProductId()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<OrderItem> findByProductIdAndProductOptionId(Long productId, Long productOptionId) {
        if (productId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> productId.equals(item.getProductId()) && 
                          Objects.equals(productOptionId, item.getProductOptionId()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public Optional<OrderItem> findByOrderIdAndProductIdAndProductOptionId(Long orderId, Long productId, Long productOptionId) {
        if (orderId == null || productId == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(item -> orderId.equals(item.getOrderId()) && 
                          productId.equals(item.getProductId()) &&
                          Objects.equals(productOptionId, item.getProductOptionId()))
            .findFirst();
    }
    
    @Override
    public List<OrderItem> findByState(OrderState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> state.equals(item.getState()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<OrderItem> findItemsWithDiscount() {
        return store.values().stream()
            .filter(OrderItem::hasDiscount)
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<OrderItem> findAll() {
        return store.values().stream()
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
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
    public boolean existsByOrderId(Long orderId) {
        if (orderId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(item -> orderId.equals(item.getOrderId()));
    }
    
    @Override
    public boolean existsByOrderIdAndProductIdAndProductOptionId(Long orderId, Long productId, Long productOptionId) {
        if (orderId == null || productId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(item -> orderId.equals(item.getOrderId()) && 
                            productId.equals(item.getProductId()) &&
                            Objects.equals(productOptionId, item.getProductOptionId()));
    }
    
    @Override
    public void deleteById(Long id) {
        if (id != null) {
            store.remove(id);
        }
    }
    
    @Override
    public void deleteByOrderId(Long orderId) {
        if (orderId == null) {
            return;
        }
        
        List<Long> idsToDelete = store.values().stream()
            .filter(item -> orderId.equals(item.getOrderId()))
            .map(OrderItem::getId)
            .collect(Collectors.toList());
        
        idsToDelete.forEach(store::remove);
    }
    
    @Override
    public void deleteByProductId(Long productId) {
        if (productId == null) {
            return;
        }
        
        List<Long> idsToDelete = store.values().stream()
            .filter(item -> productId.equals(item.getProductId()))
            .map(OrderItem::getId)
            .collect(Collectors.toList());
        
        idsToDelete.forEach(store::remove);
    }
    
    @Override
    public long count() {
        return store.size();
    }
    
    @Override
    public long countByOrderId(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(item -> orderId.equals(item.getOrderId()))
            .count();
    }
    
    @Override
    public long countByProductId(Long productId) {
        if (productId == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(item -> productId.equals(item.getProductId()))
            .count();
    }
    
    @Override
    public long countByState(OrderState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(item -> state.equals(item.getState()))
            .count();
    }
}