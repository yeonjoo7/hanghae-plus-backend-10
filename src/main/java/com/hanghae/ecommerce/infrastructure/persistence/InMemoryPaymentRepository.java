package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.payment.PaymentState;
import com.hanghae.ecommerce.domain.payment.repository.PaymentRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 결제 Repository 구현체
 */
@Repository
public class InMemoryPaymentRepository implements PaymentRepository {
    
    private final ConcurrentHashMap<Long, Payment> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public Payment save(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("결제 정보는 null일 수 없습니다.");
        }
        
        Payment savedPayment;
        if (payment.getId() == null) {
            // 새로운 결제 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedPayment = Payment.restore(
                newId,
                payment.getOrderId(),
                payment.getState(),
                payment.getMethod(),
                payment.getPaidAmount(),
                payment.getPaidAt(),
                payment.getExpiresAt(),
                payment.getCreatedAt(),
                now
            );
        } else {
            // 기존 결제 업데이트
            savedPayment = Payment.restore(
                payment.getId(),
                payment.getOrderId(),
                payment.getState(),
                payment.getMethod(),
                payment.getPaidAmount(),
                payment.getPaidAt(),
                payment.getExpiresAt(),
                payment.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedPayment.getId(), savedPayment);
        return savedPayment;
    }
    
    @Override
    public Optional<Payment> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public List<Payment> findByOrderId(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(payment -> orderId.equals(payment.getOrderId()))
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Payment> findByOrderIdAndState(Long orderId, PaymentState state) {
        if (orderId == null || state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(payment -> orderId.equals(payment.getOrderId()) && 
                             state.equals(payment.getState()))
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Payment> findByState(PaymentState state) {
        if (state == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(payment -> state.equals(payment.getState()))
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Payment> findByMethod(PaymentMethod method) {
        if (method == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(payment -> method.equals(payment.getMethod()))
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Payment> findPendingPayments() {
        return findByState(PaymentState.PENDING);
    }
    
    @Override
    public List<Payment> findCompletedPayments() {
        return findByState(PaymentState.COMPLETED);
    }
    
    @Override
    public List<Payment> findFailedPayments() {
        return findByState(PaymentState.FAILED);
    }
    
    @Override
    public List<Payment> findExpiredPayments(LocalDateTime now) {
        if (now == null) {
            now = LocalDateTime.now();
        }
        
        final LocalDateTime checkTime = now;
        return store.values().stream()
            .filter(payment -> payment.getExpiresAt() != null && 
                             checkTime.isAfter(payment.getExpiresAt()))
            .sorted((p1, p2) -> p2.getExpiresAt().compareTo(p1.getExpiresAt())) // 최근 만료순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Payment> findExpiringPayments(LocalDateTime expiryThreshold) {
        if (expiryThreshold == null) {
            return List.of();
        }
        
        LocalDateTime now = LocalDateTime.now();
        return store.values().stream()
            .filter(payment -> payment.getExpiresAt() != null &&
                             payment.getState() == PaymentState.PENDING &&
                             payment.getExpiresAt().isBefore(expiryThreshold) &&
                             payment.getExpiresAt().isAfter(now))
            .sorted((p1, p2) -> p1.getExpiresAt().compareTo(p2.getExpiresAt())) // 만료일이 빠른 순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Payment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(payment -> !payment.getCreatedAt().isBefore(startDate) && 
                             !payment.getCreatedAt().isAfter(endDate))
            .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Payment> findAll() {
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
    public boolean existsByOrderId(Long orderId) {
        if (orderId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(payment -> orderId.equals(payment.getOrderId()));
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
            .filter(payment -> orderId.equals(payment.getOrderId()))
            .map(Payment::getId)
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
            .filter(payment -> orderId.equals(payment.getOrderId()))
            .count();
    }
    
    @Override
    public long countByState(PaymentState state) {
        if (state == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(payment -> state.equals(payment.getState()))
            .count();
    }
    
    @Override
    public long countByMethod(PaymentMethod method) {
        if (method == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(payment -> method.equals(payment.getMethod()))
            .count();
    }
    
    @Override
    public long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(payment -> !payment.getCreatedAt().isBefore(startDate) && 
                             !payment.getCreatedAt().isAfter(endDate))
            .count();
    }

    @Override
    public boolean existsByOrderIdAndState(Long orderId, PaymentState state) {
        if (orderId == null || state == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(payment -> orderId.equals(payment.getOrderId()) && 
                               state.equals(payment.getState()));
    }
}