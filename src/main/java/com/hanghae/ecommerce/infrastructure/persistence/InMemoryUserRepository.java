package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.UserState;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 사용자 Repository 구현체
 */
@Repository
public class InMemoryUserRepository implements UserRepository {
    
    private final ConcurrentHashMap<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public User save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("사용자 정보는 null일 수 없습니다.");
        }
        
        User savedUser;
        if (user.getId() == null) {
            // 새로운 사용자 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedUser = User.restore(
                newId,
                user.getEmail(),
                user.getState(),
                user.getType(),
                user.getName(),
                user.getPhone(),
                user.getAvailablePoint(),
                user.getUsedPoint(),
                user.getCreatedAt(),
                now
            );
        } else {
            // 기존 사용자 업데이트
            savedUser = User.restore(
                user.getId(),
                user.getEmail(),
                user.getState(),
                user.getType(),
                user.getName(),
                user.getPhone(),
                user.getAvailablePoint(),
                user.getUsedPoint(),
                user.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedUser.getId(), savedUser);
        return savedUser;
    }
    
    @Override
    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(user -> email.equals(user.getEmail()))
            .findFirst();
    }
    
    @Override
    public Optional<User> findByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return Optional.empty();
        }
        
        return store.values().stream()
            .filter(user -> phone.equals(user.getPhone()))
            .findFirst();
    }
    
    @Override
    public List<User> findByState(UserState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(user -> state.equals(user.getState()))
            .sorted((u1, u2) -> u2.getCreatedAt().compareTo(u1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<User> findActiveUsers() {
        return store.values().stream()
            .filter(User::isActive)
            .sorted((u1, u2) -> u2.getCreatedAt().compareTo(u1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<User> findAll() {
        return store.values().stream()
            .sorted((u1, u2) -> u2.getCreatedAt().compareTo(u1.getCreatedAt())) // 최신순
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
    public boolean existsByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(user -> email.equals(user.getEmail()));
    }
    
    @Override
    public boolean existsByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(user -> phone.equals(user.getPhone()));
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
    public long countByState(UserState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(user -> state.equals(user.getState()))
            .count();
    }
}