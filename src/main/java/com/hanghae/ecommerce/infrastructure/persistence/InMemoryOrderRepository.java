package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.order.OrderNumber;
import com.hanghae.ecommerce.domain.order.OrderState;
import com.hanghae.ecommerce.domain.order.repository.OrderRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 주문 Repository 구현체
 */
@Repository
public class InMemoryOrderRepository implements OrderRepository {
    
    private final ConcurrentHashMap<Long, Order> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public Order save(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("주문 정보는 null일 수 없습니다.");
        }
        
        Order savedOrder;
        if (order.getId() == null) {
            // 새로운 주문 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedOrder = Order.restore(
                newId,
                order.getUserId(),
                order.getUserCouponId(),
                order.getCartId(),
                order.getOrderNumber(),
                order.getState(),
                order.getAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getRecipient(),
                order.getAddress(),
                order.getCreatedAt(),
                now
            );
        } else {
            // 기존 주문 업데이트
            savedOrder = Order.restore(
                order.getId(),
                order.getUserId(),
                order.getUserCouponId(),
                order.getCartId(),
                order.getOrderNumber(),
                order.getState(),
                order.getAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getRecipient(),
                order.getAddress(),
                order.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedOrder.getId(), savedOrder);
        return savedOrder;
    }
    
    @Override
    public Optional<Order> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public Optional<Order> findByOrderNumber(OrderNumber orderNumber) {
        if (orderNumber == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(order -> orderNumber.equals(order.getOrderNumber()))
            .findFirst();
    }
    
    @Override
    public List<Order> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(order -> userId.equals(order.getUserId()))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findByUserIdAndState(Long userId, OrderState state) {
        if (userId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(order -> userId.equals(order.getUserId()) && state.equals(order.getState()))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public Optional<Order> findByCartId(Long cartId) {
        if (cartId == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(order -> cartId.equals(order.getCartId()))
            .findFirst();
    }
    
    @Override
    public List<Order> findOrdersWithCoupon() {
        return store.values().stream()
            .filter(Order::hasCouponApplied)
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findByUserCouponId(Long userCouponId) {
        if (userCouponId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(order -> userCouponId.equals(order.getUserCouponId()))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findByState(OrderState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(order -> state.equals(order.getState()))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(order -> !order.getCreatedAt().isBefore(startDate) && 
                           !order.getCreatedAt().isAfter(endDate))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findPendingPaymentOrders() {
        return findByState(OrderState.PENDING_PAYMENT);
    }
    
    @Override
    public List<Order> findCompletedOrders() {
        return findByState(OrderState.COMPLETED);
    }
    
    @Override
    public List<Order> findCancelledOrders() {
        return findByState(OrderState.CANCELLED);
    }
    
    @Override
    public List<Order> findAll() {
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
    public boolean existsByOrderNumber(OrderNumber orderNumber) {
        if (orderNumber == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(order -> orderNumber.equals(order.getOrderNumber()));
    }
    
    @Override
    public boolean existsByUserId(Long userId) {
        if (userId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(order -> userId.equals(order.getUserId()));
    }
    
    @Override
    public void deleteById(Long id) {
        if (id != null) {
            store.remove(id);
        }
    }
    
    @Override
    public void deleteByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        
        List<Long> idsToDelete = store.values().stream()
            .filter(order -> userId.equals(order.getUserId()))
            .map(Order::getId)
            .collect(Collectors.toList());
        
        idsToDelete.forEach(store::remove);
    }
    
    @Override
    public long count() {
        return store.size();
    }
    
    @Override
    public long countByUserId(Long userId) {
        if (userId == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(order -> userId.equals(order.getUserId()))
            .count();
    }
    
    @Override
    public long countByState(OrderState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(order -> state.equals(order.getState()))
            .count();
    }
    
    @Override
    public long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(order -> !order.getCreatedAt().isBefore(startDate) && 
                           !order.getCreatedAt().isAfter(endDate))
            .count();
    }

    @Override
    public List<Order> findByUserIdAndStateAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, OrderState state, LocalDateTime startDate, LocalDateTime endDate) {
        if (userId == null || state == null || startDate == null || endDate == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(order -> userId.equals(order.getUserId()) &&
                           state.equals(order.getState()) &&
                           !order.getCreatedAt().isBefore(startDate) &&
                           !order.getCreatedAt().isAfter(endDate))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        if (userId == null || startDate == null || endDate == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(order -> userId.equals(order.getUserId()) &&
                           !order.getCreatedAt().isBefore(startDate) &&
                           !order.getCreatedAt().isAfter(endDate))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByUserIdAndStateOrderByCreatedAtDesc(Long userId, OrderState state) {
        if (userId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(order -> userId.equals(order.getUserId()) && state.equals(order.getState()))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByUserIdOrderByCreatedAtDesc(Long userId) {
        if (userId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(order -> userId.equals(order.getUserId()))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());
    }
}