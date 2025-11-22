package com.hanghae.ecommerce.domain.payment.repository;

import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.payment.PaymentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 Repository - Spring Data JPA
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

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
    @Query("SELECT p FROM Payment p WHERE p.state = 'PENDING' ORDER BY p.createdAt DESC")
    List<Payment> findPendingPayments();

    /**
     * 완료된 결제 목록 조회
     */
    @Query("SELECT p FROM Payment p WHERE p.state = 'COMPLETED' ORDER BY p.createdAt DESC")
    List<Payment> findCompletedPayments();

    /**
     * 실패한 결제 목록 조회
     */
    @Query("SELECT p FROM Payment p WHERE p.state = 'FAILED' ORDER BY p.createdAt DESC")
    List<Payment> findFailedPayments();

    /**
     * 만료된 결제 목록 조회
     */
    @Query("SELECT p FROM Payment p WHERE p.state = 'PENDING' AND p.createdAt < :now")
    List<Payment> findExpiredPayments(@Param("now") LocalDateTime now);

    /**
     * 만료 예정 결제 목록 조회
     */
    @Query("SELECT p FROM Payment p WHERE p.state = 'PENDING' AND p.createdAt < :expiryThreshold")
    List<Payment> findExpiringPayments(@Param("expiryThreshold") LocalDateTime expiryThreshold);

    /**
     * 기간별 결제 목록 조회
     */
    List<Payment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 주문 결제 존재 여부 확인
     */
    boolean existsByOrderId(Long orderId);

    /**
     * 주문 ID와 상태로 결제 존재 여부 확인
     */
    boolean existsByOrderIdAndState(Long orderId, PaymentState state);

    /**
     * 특정 주문의 모든 결제 삭제
     */
    @Modifying
    @Query("DELETE FROM Payment p WHERE p.orderId = :orderId")
    void deleteByOrderId(@Param("orderId") Long orderId);

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
}