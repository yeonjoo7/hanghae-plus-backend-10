package com.hanghae.ecommerce.infrastructure.persistence;

import com.hanghae.ecommerce.domain.payment.BalanceTransaction;
import com.hanghae.ecommerce.domain.payment.TransactionType;
import com.hanghae.ecommerce.domain.payment.repository.BalanceTransactionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 인메모리 잔액 거래 Repository 구현체
 */
@Repository
public class InMemoryBalanceTransactionRepository implements BalanceTransactionRepository {
    
    private final ConcurrentHashMap<Long, BalanceTransaction> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    
    @Override
    public BalanceTransaction save(BalanceTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("거래 정보는 null일 수 없습니다.");
        }
        
        BalanceTransaction savedTransaction;
        if (transaction.getId() == null) {
            // 새로운 거래 생성
            Long newId = idGenerator.getAndIncrement();
            LocalDateTime now = LocalDateTime.now();
            savedTransaction = BalanceTransaction.restore(
                newId,
                transaction.getUserId(),
                transaction.getOrderId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                now
            );
        } else {
            // 기존 거래 업데이트
            savedTransaction = BalanceTransaction.restore(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getOrderId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                LocalDateTime.now()
            );
        }
        
        store.put(savedTransaction.getId(), savedTransaction);
        return savedTransaction;
    }
    
    @Override
    public Optional<BalanceTransaction> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }
    
    @Override
    public List<BalanceTransaction> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(transaction -> userId.equals(transaction.getUserId()))
            .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<BalanceTransaction> findByUserIdAndType(Long userId, TransactionType type) {
        if (userId == null || type == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(transaction -> userId.equals(transaction.getUserId()) && 
                                 type.equals(transaction.getType()))
            .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<BalanceTransaction> findByOrderId(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(transaction -> orderId.equals(transaction.getOrderId()))
            .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<BalanceTransaction> findByType(TransactionType type) {
        if (type == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(transaction -> type.equals(transaction.getType()))
            .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<BalanceTransaction> findChargeTransactions() {
        return findByType(TransactionType.CHARGE);
    }
    
    @Override
    public List<BalanceTransaction> findPaymentTransactions() {
        return findByType(TransactionType.PAYMENT);
    }
    
    @Override
    public List<BalanceTransaction> findRefundTransactions() {
        return findByType(TransactionType.REFUND);
    }
    
    @Override
    public List<BalanceTransaction> findOrderRelatedTransactions() {
        return store.values().stream()
            .filter(BalanceTransaction::isOrderRelated)
            .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<BalanceTransaction> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(transaction -> !transaction.getCreatedAt().isBefore(startDate) && 
                                 !transaction.getCreatedAt().isAfter(endDate))
            .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<BalanceTransaction> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        if (userId == null || startDate == null || endDate == null) {
            return List.of();
        }
        
        return store.values().stream()
            .filter(transaction -> userId.equals(transaction.getUserId()) &&
                                 !transaction.getCreatedAt().isBefore(startDate) && 
                                 !transaction.getCreatedAt().isAfter(endDate))
            .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // 최신순
            .collect(Collectors.toList());
    }
    
    @Override
    public List<BalanceTransaction> findAll() {
        return store.values().stream()
            .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt())) // 최신순
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
            .anyMatch(transaction -> userId.equals(transaction.getUserId()));
    }
    
    @Override
    public boolean existsByOrderId(Long orderId) {
        if (orderId == null) {
            return false;
        }
        
        return store.values().stream()
            .anyMatch(transaction -> orderId.equals(transaction.getOrderId()));
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
            .filter(transaction -> userId.equals(transaction.getUserId()))
            .map(BalanceTransaction::getId)
            .collect(Collectors.toList());
        
        idsToDelete.forEach(store::remove);
    }
    
    @Override
    public void deleteByOrderId(Long orderId) {
        if (orderId == null) {
            return;
        }
        
        List<Long> idsToDelete = store.values().stream()
            .filter(transaction -> orderId.equals(transaction.getOrderId()))
            .map(BalanceTransaction::getId)
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
            .filter(transaction -> userId.equals(transaction.getUserId()))
            .count();
    }
    
    @Override
    public long countByOrderId(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(transaction -> orderId.equals(transaction.getOrderId()))
            .count();
    }
    
    @Override
    public long countByType(TransactionType type) {
        if (type == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(transaction -> type.equals(transaction.getType()))
            .count();
    }
    
    @Override
    public long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        
        return store.values().stream()
            .filter(transaction -> !transaction.getCreatedAt().isBefore(startDate) && 
                                 !transaction.getCreatedAt().isAfter(endDate))
            .count();
    }
}