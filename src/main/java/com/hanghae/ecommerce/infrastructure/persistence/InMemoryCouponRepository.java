package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.CouponState;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 쿠폰 Repository 구현체
 */
@Repository
public class InMemoryCouponRepository implements CouponRepository {
    
    private final ConcurrentHashMap<Long, Coupon> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public Coupon save(Coupon coupon) {
        if (coupon == null) {
            throw new IllegalArgumentException("쿠폰 정보는 null일 수 없습니다.");
        }
        
        Coupon savedCoupon;
        if (coupon.getId() == null) {
            // 새로운 쿠폰 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedCoupon = Coupon.restore(
                newId,
                coupon.getName(),
                coupon.getState(),
                coupon.getDiscountPolicy(),
                coupon.getTotalQuantity(),
                coupon.getIssuedQuantity(),
                coupon.getBeginDate(),
                coupon.getEndDate(),
                coupon.getCreatedAt(),
                now
            );
        } else {
            // 기존 쿠폰 업데이트
            savedCoupon = Coupon.restore(
                coupon.getId(),
                coupon.getName(),
                coupon.getState(),
                coupon.getDiscountPolicy(),
                coupon.getTotalQuantity(),
                coupon.getIssuedQuantity(),
                coupon.getBeginDate(),
                coupon.getEndDate(),
                coupon.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedCoupon.getId(), savedCoupon);
        return savedCoupon;
    }
    
    @Override
    public Optional<Coupon> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public List<Coupon> findByNameContaining(String name) {
        if (name == null || name.trim().isEmpty()) {
            return List.of();
        }
        
        String searchName = name.toLowerCase();
        return store.values().stream()
            .filter(coupon -> coupon.getName().toLowerCase().contains(searchName))
            .sorted((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Coupon> findByState(CouponState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(coupon -> state.equals(coupon.getState()))
            .sorted((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Coupon> findIssuableCoupons() {
        return store.values().stream()
            .filter(Coupon::canIssue)
            .sorted((c1, c2) -> c1.getEndDate().compareTo(c2.getEndDate())) // 만료일이 빠른 순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Coupon> findValidCoupons(LocalDateTime now) {
        if (now == null) {
            now = LocalDateTime.now();
        }
        
        final LocalDateTime checkTime = now;
        return store.values().stream()
            .filter(coupon -> !checkTime.isBefore(coupon.getBeginDate()) && 
                            !checkTime.isAfter(coupon.getEndDate()))
            .sorted((c1, c2) -> c1.getEndDate().compareTo(c2.getEndDate())) // 만료일이 빠른 순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Coupon> findExpiredCoupons(LocalDateTime now) {
        if (now == null) {
            now = LocalDateTime.now();
        }
        
        final LocalDateTime checkTime = now;
        return store.values().stream()
            .filter(coupon -> checkTime.isAfter(coupon.getEndDate()))
            .sorted((c1, c2) -> c2.getEndDate().compareTo(c1.getEndDate())) // 최근 만료 순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Coupon> findCouponsWithRemainingQuantity() {
        return store.values().stream()
            .filter(Coupon::hasRemainingQuantity)
            .sorted((c1, c2) -> c1.getEndDate().compareTo(c2.getEndDate())) // 만료일이 빠른 순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Coupon> findAll() {
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
    public boolean existsByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(coupon -> name.equals(coupon.getName()));
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
    public long countByState(CouponState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(coupon -> state.equals(coupon.getState()))
            .count();
    }
    
    @Override
    public Optional<Coupon> findByIdForUpdate(Long id) {
        // 인메모리 구현에서는 락을 시뮬레이션할 수 없으므로 일반 조회와 동일하게 처리
        return findById(id);
    }
}