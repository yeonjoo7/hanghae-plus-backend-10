package com.hanghae.ecommerce.domain.payment.repository;

import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.payment.PaymentState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 Repository 인터페이스
 */
public interface PaymentRepository {
    
    /**
     * 결제 저장
     */
    Payment save(Payment payment);
    
    /**
     * ID로 결제 조회
     */
    Optional<Payment> findById(Long id);
    
    /**
     * 주문 ID로 결제 목록 조회
     */
    List<Payment> findByOrderId(Long orderId);
    
    /**
     * 주문 ID와 상태로 결제 조회
     */
    List<Payment> findByOrderIdAndState(Long orderId, PaymentState state);
    
    /**
     * 상태별 결제 목록 조회
     */
    List<Payment> findByState(PaymentState state);
    
    /**
     * 결제 수단별 결제 목록 조회
     */
    List<Payment> findByMethod(PaymentMethod method);
    
    /**
     * 결제 대기 중인 결제 목록 조회
     */
    List<Payment> findPendingPayments();
    
    /**
     * 완료된 결제 목록 조회
     */
    List<Payment> findCompletedPayments();
    
    /**
     * 실패한 결제 목록 조회
     */
    List<Payment> findFailedPayments();
    
    /**
     * 만료된 결제 목록 조회
     */
    List<Payment> findExpiredPayments(LocalDateTime now);
    
    /**
     * 만료 예정 결제 목록 조회
     */
    List<Payment> findExpiringPayments(LocalDateTime expiryThreshold);
    
    /**
     * 기간별 결제 목록 조회
     */
    List<Payment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * 모든 결제 조회
     */
    List<Payment> findAll();
    
    /**
     * 결제 존재 여부 확인
     */
    boolean existsById(Long id);
    
    /**
     * 주문 결제 존재 여부 확인
     */
    boolean existsByOrderId(Long orderId);
    
    /**
     * 결제 삭제
     */
    void deleteById(Long id);
    
    /**
     * 특정 주문의 모든 결제 삭제
     */
    void deleteByOrderId(Long orderId);
    
    /**
     * 전체 결제 수 조회
     */
    long count();
    
    /**
     * 주문별 결제 수 조회
     */
    long countByOrderId(Long orderId);
    
    /**
     * 상태별 결제 수 조회
     */
    long countByState(PaymentState state);
    
    /**
     * 결제 수단별 결제 수 조회
     */
    long countByMethod(PaymentMethod method);
    
    /**
     * 기간별 결제 수 조회
     */
    long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * 주문 ID와 상태로 결제 존재 여부 확인
     */
    boolean existsByOrderIdAndState(Long orderId, PaymentState state);
}