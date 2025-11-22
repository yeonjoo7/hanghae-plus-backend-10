package com.hanghae.ecommerce.domain.payment.repository;

import com.hanghae.ecommerce.domain.payment.BalanceTransaction;
import com.hanghae.ecommerce.domain.payment.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 잔액 거래 Repository - Spring Data JPA
 */
public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {

    /**
     * 사용자 ID로 거래 목록 조회
     */
    List<BalanceTransaction> findByUserId(Long userId);

    /**
     * 사용자 ID와 거래 유형으로 거래 목록 조회
     */
    List<BalanceTransaction> findByUserIdAndType(Long userId, TransactionType type);

    /**
     * 주문 ID로 거래 목록 조회
     */
    List<BalanceTransaction> findByOrderId(Long orderId);

    /**
     * 거래 유형별 거래 목록 조회
     */
    List<BalanceTransaction> findByType(TransactionType type);

    // /*
    // // Original queries for charge and payment transactions
    // // /**
    // // * 충전 거래 목록 조회
    // // */
    // // @Query("SELECT bt FROM BalanceTransaction bt WHERE bt.type =
    // com.hanghae.ecommerce.domain.payment.TransactionType.CHARGE ORDER BY
    // bt.createdAt DESC")
    // // List<BalanceTransaction> findChargeTransactions();
    //
    // // /**
    // // * 결제 거래 목록 조회
    // // */
    // // @Query("SELECT bt FROM BalanceTransaction bt WHERE bt.type =
    // com.hanghae.ecommerce.domain.payment.TransactionType.PAYMENT ORDER BY
    // bt.createdAt DESC")
    // // List<BalanceTransaction> findPaymentTransactions();
    //
    // // New queries to be commented out
    // @Query("SELECT bt FROM BalanceTransaction bt WHERE bt.userId = :userId ORDER
    // BY bt.createdAt DESC")
    // List<BalanceTransaction> findByUserIdOrderByCreatedAtDesc(@Param("userId")
    // Long userId);
    //
    // @Query("SELECT bt FROM BalanceTransaction bt WHERE bt.userId = :userId AND
    // bt.type = com.hanghae.ecommerce.domain.payment.TransactionType.CHARGE ORDER
    // BY bt.createdAt DESC")
    // List<BalanceTransaction> findChargeHistoryByUserId(@Param("userId") Long
    // userId);
    // */

    /**
     * 환불 거래 목록 조회
     */
    @Query("SELECT bt FROM BalanceTransaction bt WHERE bt.type = com.hanghae.ecommerce.domain.payment.TransactionType.REFUND ORDER BY bt.createdAt DESC")
    List<BalanceTransaction> findRefundTransactions();

    /**
     * 주문과 연관된 거래 목록 조회
     */
    @Query("SELECT bt FROM BalanceTransaction bt WHERE bt.orderId IS NOT NULL ORDER BY bt.createdAt DESC")
    List<BalanceTransaction> findOrderRelatedTransactions();

    /**
     * 기간별 거래 목록 조회
     */
    List<BalanceTransaction> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 사용자의 기간별 거래 목록 조회
     */
    List<BalanceTransaction> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime startDate,
            LocalDateTime endDate);

    /**
     * 사용자 거래 존재 여부 확인
     */
    boolean existsByUserId(Long userId);

    /**
     * 주문 관련 거래 존재 여부 확인
     */
    boolean existsByOrderId(Long orderId);

    /**
     * 사용자의 모든 거래 삭제
     */
    @Modifying
    @Query("DELETE FROM BalanceTransaction bt WHERE bt.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 특정 주문의 모든 거래 삭제
     */
    @Modifying
    @Query("DELETE FROM BalanceTransaction bt WHERE bt.orderId = :orderId")
    void deleteByOrderId(@Param("orderId") Long orderId);

    /**
     * 사용자별 거래 수 조회
     */
    long countByUserId(Long userId);

    /**
     * 주문별 거래 수 조회
     */
    long countByOrderId(Long orderId);

    /**
     * 거래 유형별 거래 수 조회
     */
    long countByType(TransactionType type);

    /**
     * 기간별 거래 수 조회
     */
    long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
}