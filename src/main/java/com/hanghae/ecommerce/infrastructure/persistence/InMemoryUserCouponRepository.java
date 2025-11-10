package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 사용자 쿠폰 Repository 구현체
 */
@Repository
public class InMemoryUserCouponRepository implements UserCouponRepository {
    
    private final ConcurrentHashMap<Long, UserCoupon> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon == null) {
            throw new IllegalArgumentException("사용자 쿠폰 정보는 null일 수 없습니다.");
        }
        
        UserCoupon savedUserCoupon;
        if (userCoupon.getId() == null) {
            // 새로운 사용자 쿠폰 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedUserCoupon = UserCoupon.restore(
                newId,
                userCoupon.getUserId(),
                userCoupon.getCouponId(),
                userCoupon.getState(),
                userCoupon.getIssuedAt(),
                userCoupon.getUsedAt(),
                userCoupon.getExpiresAt(),
                userCoupon.getCreatedAt(),
                now
            );
        } else {
            // 기존 사용자 쿠폰 업데이트
            savedUserCoupon = UserCoupon.restore(
                userCoupon.getId(),
                userCoupon.getUserId(),
                userCoupon.getCouponId(),
                userCoupon.getState(),
                userCoupon.getIssuedAt(),
                userCoupon.getUsedAt(),
                userCoupon.getExpiresAt(),
                userCoupon.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedUserCoupon.getId(), savedUserCoupon);
        return savedUserCoupon;
    }
    
    @Override
    public Optional<UserCoupon> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
            .sorted((uc1, uc2) -> uc2.getIssuedAt().compareTo(uc1.getIssuedAt())) // 최신 발급순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId) {
        if (userId == null || couponId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()) && 
                                couponId.equals(userCoupon.getCouponId()))
            .sorted((uc1, uc2) -> uc2.getIssuedAt().compareTo(uc1.getIssuedAt())) // 최신 발급순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findByUserIdAndState(Long userId, UserCouponState state) {
        if (userId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()) && 
                                state.equals(userCoupon.getState()))
            .sorted((uc1, uc2) -> uc1.getExpiresAt().compareTo(uc2.getExpiresAt())) // 만료일이 빠른 순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findByCouponId(Long couponId) {
        if (couponId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> couponId.equals(userCoupon.getCouponId()))
            .sorted((uc1, uc2) -> uc2.getIssuedAt().compareTo(uc1.getIssuedAt())) // 최신 발급순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findAvailableCouponsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()) && userCoupon.canUse())
            .sorted((uc1, uc2) -> uc1.getExpiresAt().compareTo(uc2.getExpiresAt())) // 만료일이 빠른 순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findExpiredCouponsByUserId(Long userId, LocalDateTime now) {
        if (userId == null) {
            return List.of();
        }
        
        if (now == null) {
            now = LocalDateTime.now();
        }
        
        final LocalDateTime checkTime = now;
        return store.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()) && 
                                checkTime.isAfter(userCoupon.getExpiresAt()))
            .sorted((uc1, uc2) -> uc2.getExpiresAt().compareTo(uc1.getExpiresAt())) // 최근 만료순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findExpiringCoupons(LocalDateTime expiryThreshold) {
        if (expiryThreshold == null) {
            return List.of();
        }
        
        LocalDateTime now = LocalDateTime.now();
        return store.values().stream()
            .filter(userCoupon -> userCoupon.canUse() && 
                                userCoupon.getExpiresAt().isBefore(expiryThreshold) &&
                                userCoupon.getExpiresAt().isAfter(now))
            .sorted((uc1, uc2) -> uc1.getExpiresAt().compareTo(uc2.getExpiresAt())) // 만료일이 빠른 순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findByState(UserCouponState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> state.equals(userCoupon.getState()))
            .sorted((uc1, uc2) -> uc2.getIssuedAt().compareTo(uc1.getIssuedAt())) // 최신 발급순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findAll() {
        return store.values().stream()
            .sorted((uc1, uc2) -> uc2.getIssuedAt().compareTo(uc1.getIssuedAt())) // 최신 발급순
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
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        if (userId == null || couponId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(userCoupon -> userId.equals(userCoupon.getUserId()) && 
                                  couponId.equals(userCoupon.getCouponId()));
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
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
            .map(UserCoupon::getId)
            .collect(Collectors.toList());
        
        idsToDelete.forEach(store::remove);
    }
    
    @Override
    public void deleteByCouponId(Long couponId) {
        if (couponId == null) {
            return;
        }
        
        List<Long> idsToDelete = store.values().stream()
            .filter(userCoupon -> couponId.equals(userCoupon.getCouponId()))
            .map(UserCoupon::getId)
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
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
            .count();
    }
    
    @Override
    public long countByCouponId(Long couponId) {
        if (couponId == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(userCoupon -> couponId.equals(userCoupon.getCouponId()))
            .count();
    }
    
    @Override
    public long countByState(UserCouponState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(userCoupon -> state.equals(userCoupon.getState()))
            .count();
    }
    
    @Override
    public List<UserCoupon> findByUserIdOrderByIssuedAtDesc(Long userId) {
        if (userId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
            .sorted((uc1, uc2) -> uc2.getIssuedAt().compareTo(uc1.getIssuedAt())) // 발급일시 내림차순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findByUserIdAndStateOrderByIssuedAtDesc(Long userId, UserCouponState state) {
        if (userId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()) && state.equals(userCoupon.getState()))
            .sorted((uc1, uc2) -> uc2.getIssuedAt().compareTo(uc1.getIssuedAt())) // 발급일시 내림차순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findByUserIdAndStateOrderByUsedAtDesc(Long userId, UserCouponState state) {
        if (userId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> userId.equals(userCoupon.getUserId()) && state.equals(userCoupon.getState()))
            .sorted((uc1, uc2) -> {
                LocalDateTime usedAt1 = uc1.getUsedAt();
                LocalDateTime usedAt2 = uc2.getUsedAt();
                if (usedAt1 == null && usedAt2 == null) return 0;
                if (usedAt1 == null) return 1;
                if (usedAt2 == null) return -1;
                return usedAt2.compareTo(usedAt1); // 사용일시 내림차순
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> findExpiredAvailableCoupons(LocalDateTime now) {
        if (now == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(userCoupon -> UserCouponState.AVAILABLE.equals(userCoupon.getState()) && 
                                now.isAfter(userCoupon.getExpiresAt()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserCoupon> saveAll(List<UserCoupon> userCoupons) {
        if (userCoupons == null || userCoupons.isEmpty()) {
            return List.of();
        }
        
        return userCoupons.stream()
            .map(this::save)
            .collect(Collectors.toList());
    }
    
    @Override
    public int countByStateAndCouponId(UserCouponState state, Long couponId) {
        if (state == null || couponId == null) {
            return 0;
        }
        
        return (int) store.values().stream()
            .filter(userCoupon -> state.equals(userCoupon.getState()) && 
                                couponId.equals(userCoupon.getCouponId()))
            .count();
    }
}