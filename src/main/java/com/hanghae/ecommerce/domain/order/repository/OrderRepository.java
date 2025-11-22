package com.hanghae.ecommerce.domain.order.repository;

import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.order.OrderNumber;
import com.hanghae.ecommerce.domain.order.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 Repository - Spring Data JPA
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 주문번호로 주문 조회
     */
    Optional<Order> findByOrderNumber(OrderNumber orderNumber);

    /**
     * 사용자 ID로 주문 목록 조회
     */
    List<Order> findByUserId(Long userId);

    /**
     * 사용자 ID와 상태로 주문 목록 조회
     */
    List<Order> findByUserIdAndState(Long userId, OrderState state);

    /**
     * 장바구니 ID로 주문 조회
     */
    Optional<Order> findByCartId(Long cartId);

    /**
     * 쿠폰이 적용된 주문 목록 조회
     */
    @Query("SELECT o FROM Order o WHERE o.userCouponId IS NOT NULL")
    List<Order> findOrdersWithCoupon();

    /**
     * 특정 쿠폰이 적용된 주문 목록 조회
     */
    List<Order> findByUserCouponId(Long userCouponId);

    /**
     * 상태별 주문 목록 조회
     */
    List<Order> findByState(OrderState state);

    /**
     * 기간별 주문 목록 조회
     */
    List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 결제 대기 중인 주문 목록 조회
     */
    @Query("SELECT o FROM Order o WHERE o.state = 'PENDING_PAYMENT' ORDER BY o.createdAt DESC")
    List<Order> findPendingPaymentOrders();

    /**
     * 완료된 주문 목록 조회
     */
    @Query("SELECT o FROM Order o WHERE o.state = 'COMPLETED' ORDER BY o.createdAt DESC")
    List<Order> findCompletedOrders();

    /**
     * 취소된 주문 목록 조회
     */
    @Query("SELECT o FROM Order o WHERE o.state = 'CANCELLED' ORDER BY o.createdAt DESC")
    List<Order> findCancelledOrders();

    /**
     * 주문번호 존재 여부 확인
     */
    boolean existsByOrderNumber(OrderNumber orderNumber);

    /**
     * 사용자 주문 존재 여부 확인
     */
    boolean existsByUserId(Long userId);

    /**
     * 사용자의 모든 주문 삭제
     */
    void deleteByUserId(Long userId);

    /**
     * 사용자별 주문 수 조회
     */
    long countByUserId(Long userId);

    /**
     * 상태별 주문 수 조회
     */
    long countByState(OrderState state);

    /**
     * 기간별 주문 수 조회
     */
    long countByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 사용자별 주문 목록 (생성일 내림차순)
     */
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 사용자 + 상태별 주문 목록 (생성일 내림차순)
     */
    List<Order> findByUserIdAndStateOrderByCreatedAtDesc(Long userId, OrderState state);

    /**
     * 사용자 + 기간별 주문 목록 (생성일 내림차순)
     */
    List<Order> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, LocalDateTime startDate,
            LocalDateTime endDate);

    /**
     * 사용자 + 상태 + 기간별 주문 목록 (생성일 내림차순)
     */
    List<Order> findByUserIdAndStateAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, OrderState state,
            LocalDateTime startDate, LocalDateTime endDate);
}