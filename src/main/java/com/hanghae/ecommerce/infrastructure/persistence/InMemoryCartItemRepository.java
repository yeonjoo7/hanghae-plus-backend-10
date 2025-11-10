package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.cart.CartItem;
import com.hanghae.ecommerce.domain.cart.CartState;
import com.hanghae.ecommerce.domain.cart.repository.CartItemRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 장바구니 아이템 Repository 구현체
 */
@Repository
public class InMemoryCartItemRepository implements CartItemRepository {
    
    private final ConcurrentHashMap<Long, CartItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public CartItem save(CartItem cartItem) {
        if (cartItem == null) {
            throw new IllegalArgumentException("장바구니 아이템 정보는 null일 수 없습니다.");
        }
        
        CartItem savedCartItem;
        if (cartItem.getId() == null) {
            // 새로운 장바구니 아이템 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedCartItem = CartItem.restore(
                newId,
                cartItem.getCartId(),
                cartItem.getProductId(),
                cartItem.getProductOptionId(),
                cartItem.getState(),
                cartItem.getQuantity(),
                cartItem.getCreatedAt(),
                now
            );
        } else {
            // 기존 장바구니 아이템 업데이트
            savedCartItem = CartItem.restore(
                cartItem.getId(),
                cartItem.getCartId(),
                cartItem.getProductId(),
                cartItem.getProductOptionId(),
                cartItem.getState(),
                cartItem.getQuantity(),
                cartItem.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedCartItem.getId(), savedCartItem);
        return savedCartItem;
    }
    
    @Override
    public Optional<CartItem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public List<CartItem> findByCartId(Long cartId) {
        if (cartId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> cartId.equals(item.getCartId()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<CartItem> findByCartIdAndState(Long cartId, CartState state) {
        if (cartId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> cartId.equals(item.getCartId()) && state.equals(item.getState()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<CartItem> findActiveItemsByCartId(Long cartId) {
        if (cartId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> cartId.equals(item.getCartId()) && item.isActive())
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<CartItem> findByProductId(Long productId) {
        if (productId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> productId.equals(item.getProductId()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<CartItem> findByProductIdAndProductOptionId(Long productId, Long productOptionId) {
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
    public Optional<CartItem> findByCartIdAndProductIdAndProductOptionId(Long cartId, Long productId, Long productOptionId) {
        if (cartId == null || productId == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(item -> cartId.equals(item.getCartId()) && 
                          productId.equals(item.getProductId()) &&
                          Objects.equals(productOptionId, item.getProductOptionId()) &&
                          item.isActive())
            .findFirst();
    }
    
    @Override
    public List<CartItem> findByState(CartState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> state.equals(item.getState()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<CartItem> findAll() {
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
    public boolean existsByCartId(Long cartId) {
        if (cartId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(item -> cartId.equals(item.getCartId()));
    }
    
    @Override
    public boolean existsByCartIdAndProductIdAndProductOptionId(Long cartId, Long productId, Long productOptionId) {
        if (cartId == null || productId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(item -> cartId.equals(item.getCartId()) && 
                            productId.equals(item.getProductId()) &&
                            Objects.equals(productOptionId, item.getProductOptionId()) &&
                            item.isActive());
    }
    
    @Override
    public void deleteById(Long id) {
        if (id != null) {
            store.remove(id);
        }
    }
    
    @Override
    public void deleteByCartId(Long cartId) {
        if (cartId == null) {
            return;
        }
        
        List<Long> idsToDelete = store.values().stream()
            .filter(item -> cartId.equals(item.getCartId()))
            .map(CartItem::getId)
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
            .map(CartItem::getId)
            .collect(Collectors.toList());
        
        idsToDelete.forEach(store::remove);
    }
    
    @Override
    public long count() {
        return store.size();
    }
    
    @Override
    public long countByCartId(Long cartId) {
        if (cartId == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(item -> cartId.equals(item.getCartId()))
            .count();
    }
    
    @Override
    public long countActiveItemsByCartId(Long cartId) {
        if (cartId == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(item -> cartId.equals(item.getCartId()) && item.isActive())
            .count();
    }
    
    @Override
    public long countByState(CartState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(item -> state.equals(item.getState()))
            .count();
    }
    
    @Override
    public Optional<CartItem> findByCartIdAndProductIdAndProductOptionIdIsNullAndState(Long cartId, Long productId, CartState state) {
        if (cartId == null || productId == null || state == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(item -> cartId.equals(item.getCartId()) && 
                          productId.equals(item.getProductId()) &&
                          item.getProductOptionId() == null &&
                          state.equals(item.getState()))
            .findFirst();
    }
    
    @Override
    public List<CartItem> saveAll(List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return List.of();
        }
        
        return cartItems.stream()
            .map(this::save)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<CartItem> findByIdInAndCartIdAndState(List<Long> ids, Long cartId, CartState state) {
        if (ids == null || ids.isEmpty() || cartId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(item -> ids.contains(item.getId()) &&
                          cartId.equals(item.getCartId()) &&
                          state.equals(item.getState()))
            .sorted((i1, i2) -> i2.getCreatedAt().compareTo(i1.getCreatedAt()))
            .collect(Collectors.toList());
    }
    
    @Override
    public Optional<CartItem> findByIdAndCartIdAndState(Long id, Long cartId, CartState state) {
        if (id == null || cartId == null || state == null) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(item -> id.equals(item.getId()) &&
                          cartId.equals(item.getCartId()) &&
                          state.equals(item.getState()))
            .findFirst();
    }
}