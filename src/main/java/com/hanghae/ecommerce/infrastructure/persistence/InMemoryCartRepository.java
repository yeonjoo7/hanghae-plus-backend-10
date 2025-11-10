package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartState;
import com.hanghae.ecommerce.domain.cart.repository.CartRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 장바구니 Repository 구현체
 */
@Repository
public class InMemoryCartRepository implements CartRepository {
    
    private final ConcurrentHashMap<Long, Cart> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public Cart save(Cart cart) {
        if (cart == null) {
            throw new IllegalArgumentException("장바구니 정보는 null일 수 없습니다.");
        }
        
        Cart savedCart;
        if (cart.getId() == null) {
            // 새로운 장바구니 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedCart = Cart.restore(
                newId,
                cart.getUserId(),
                cart.getUserCouponId(),
                cart.getState(),
                cart.getCreatedAt(),
                now
            );
        } else {
            // 기존 장바구니 업데이트
            savedCart = Cart.restore(
                cart.getId(),
                cart.getUserId(),
                cart.getUserCouponId(),
                cart.getState(),
                cart.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedCart.getId(), savedCart);
        return savedCart;
    }
    
    @Override
    public Optional<Cart> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public Optional<Cart> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(cart -> userId.equals(cart.getUserId()))
            .max((c1, c2) -> c1.getCreatedAt().compareTo(c2.getCreatedAt())); // 가장 최신 장바구니
    }
    
    @Override
    public Optional<Cart> findByUserIdAndState(Long userId, CartState state) {
        if (userId == null || state == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(cart -> userId.equals(cart.getUserId()) && state.equals(cart.getState()))
            .max((c1, c2) -> c1.getCreatedAt().compareTo(c2.getCreatedAt())); // 가장 최신
    }
    
    @Override
    public Optional<Cart> findActiveCartByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(cart -> userId.equals(cart.getUserId()) && cart.isActive())
            .max((c1, c2) -> c1.getCreatedAt().compareTo(c2.getCreatedAt())); // 가장 최신 활성 장바구니
    }
    
    @Override
    public List<Cart> findCartsWithCoupon() {
        return store.values().stream()
            .filter(Cart::hasCouponApplied)
            .sorted((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Cart> findByUserCouponId(Long userCouponId) {
        if (userCouponId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(cart -> userCouponId.equals(cart.getUserCouponId()))
            .sorted((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Cart> findByState(CartState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(cart -> state.equals(cart.getState()))
            .sorted((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Cart> findAll() {
        return store.values().stream()
            .sorted((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt())) // 최신순
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
    public boolean existsByUserId(Long userId) {
        if (userId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(cart -> userId.equals(cart.getUserId()));
    }
    
    @Override
    public boolean existsActiveCartByUserId(Long userId) {
        if (userId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(cart -> userId.equals(cart.getUserId()) && cart.isActive());
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
            .filter(cart -> userId.equals(cart.getUserId()))
            .map(Cart::getId)
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
            .filter(cart -> userId.equals(cart.getUserId()))
            .count();
    }
    
    @Override
    public long countByState(CartState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(cart -> state.equals(cart.getState()))
            .count();
    }
}