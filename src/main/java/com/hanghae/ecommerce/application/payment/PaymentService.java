package com.hanghae.ecommerce.application.payment;

import com.hanghae.ecommerce.presentation.dto.PaymentResultDto;
import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.order.OrderState;
import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import com.hanghae.ecommerce.domain.order.repository.OrderRepository;
import com.hanghae.ecommerce.domain.payment.BalanceTransaction;
import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.payment.repository.BalanceTransactionRepository;
import com.hanghae.ecommerce.domain.payment.repository.PaymentRepository;
import com.hanghae.ecommerce.domain.user.Point;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import com.hanghae.ecommerce.presentation.exception.InsufficientBalanceException;
import com.hanghae.ecommerce.presentation.exception.InsufficientStockException;
import com.hanghae.ecommerce.presentation.exception.OrderNotFoundException;
import com.hanghae.ecommerce.presentation.exception.PaymentAlreadyCompletedException;
import com.hanghae.ecommerce.application.product.StockService;
import com.hanghae.ecommerce.application.coupon.CouponService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 결제 처리 서비스
 *
 * 분산락을 사용하여 동시 결제 요청에 대한 동시성 제어를 제공합니다.
 * - payment:{userId} 락: 동일 사용자의 결제를 직렬화하여 잔액 정합성 보장 및 중복 결제 방지
 *
 * 트랜잭션은 핵심 비즈니스 로직만 포함하고, 부가 로직(랭킹 업데이트, 외부 전송)은
 * 이벤트를 통해 트랜잭션 커밋 후 비동기로 처리합니다.
 */
@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final StockService stockService;
    private final CouponService couponService;
    private final LockManager lockManager;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            BalanceTransactionRepository balanceTransactionRepository,
            StockService stockService,
            CouponService couponService,
            LockManager lockManager,
            PlatformTransactionManager transactionManager,
            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.balanceTransactionRepository = balanceTransactionRepository;
        this.stockService = stockService;
        this.couponService = couponService;
        this.lockManager = lockManager;
        this.transactionManager = transactionManager;
        this.eventPublisher = eventPublisher;
    }

    public PaymentResultDto processPayment(Long userId, Long orderId, PaymentMethod paymentMethod,
            List<Long> couponIds) {
        Payment payment = processPayment(String.valueOf(orderId), String.valueOf(userId), paymentMethod);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return new PaymentResultDto(
                payment,
                order,
                List.of(),
                true,
                null
        );
    }

    /**
     * 결제 처리 (분산락 적용)
     *
     * 동시 결제 요청에 대한 동시성 제어:
     * - payment:{userId} 락: 동일 사용자의 결제를 직렬화하여 잔액 정합성 보장 및 중복 결제 방지
     */
    public Payment processPayment(String orderId, String userId, PaymentMethod paymentMethod) {
        // 1. 주문 조회 (락 획득 전 기본 검증)
        Order order = orderRepository.findById(Long.valueOf(orderId))
                .orElseThrow(() -> new OrderNotFoundException(Long.valueOf(orderId)));

        // 사용자 확인
        if (!order.getUserId().equals(Long.valueOf(userId))) {
            throw new IllegalArgumentException("잘못된 사용자 요청입니다.");
        }

        // 이미 결제된 주문인지 확인 (락 획득 전 빠른 검증)
        if (order.getState() != OrderState.PENDING_PAYMENT) {
            throw new PaymentAlreadyCompletedException();
        }

        // 2. 사용자별 결제 락 획득 - 동일 사용자의 결제를 직렬화
        String lockKey = "payment:" + userId;

        return lockManager.executeWithLock(lockKey, () -> {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            return template.execute(status -> {
                // 3. 주문 상태 재확인 (락 획득 후 - Race Condition 방지)
                Order lockedOrder = orderRepository.findById(Long.valueOf(orderId))
                        .orElseThrow(() -> new OrderNotFoundException(Long.valueOf(orderId)));

                if (lockedOrder.getState() != OrderState.PENDING_PAYMENT) {
                    throw new PaymentAlreadyCompletedException();
                }

                // 4. 사용자 잔액 확인 및 차감
                User user = userRepository.findById(Long.valueOf(userId))
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

                if (user.getAvailablePoint().getValue() < lockedOrder.getTotalAmount().getValue()) {
                    throw new InsufficientBalanceException(lockedOrder.getTotalAmount().getValue(),
                            user.getAvailablePoint().getValue());
                }

                Point beforeBalance = user.getAvailablePoint();
                user.usePoint(Point.of(lockedOrder.getTotalAmount().getValue()));
                userRepository.save(user);

                // 5. 재고 차감
                var orderItems = orderItemRepository.findByOrderId(Long.valueOf(orderId));
                Map<Long, Integer> stockReductions = new HashMap<>();
                for (var item : orderItems) {
                    stockReductions.put(item.getProductId(), item.getQuantity().getValue());
                }

                try {
                    stockService.reduceStocks(stockReductions);
                } catch (IllegalArgumentException e) {
                    throw new InsufficientStockException(0, 0);
                }

                // 6. 쿠폰 사용 처리
                if (lockedOrder.getUserCouponId() != null) {
                    couponService.useCoupon(lockedOrder.getUserCouponId(), Long.valueOf(userId));
                }

                // 7. 결제 정보 생성
                Payment payment = Payment.create(
                        Long.valueOf(orderId),
                        paymentMethod,
                        lockedOrder.getTotalAmount(),
                        null
                );
                payment.complete();
                paymentRepository.save(payment);

                // 8. 잔액 거래 내역 저장
                BalanceTransaction transaction = BalanceTransaction.createPayment(
                        Long.valueOf(userId),
                        Long.valueOf(orderId),
                        Point.of(lockedOrder.getTotalAmount().getValue()),
                        beforeBalance,
                        "주문 결제: " + lockedOrder.getOrderNumber().getValue());
                balanceTransactionRepository.save(transaction);

                // 9. 주문 상태 변경
                lockedOrder.complete();
                orderRepository.save(lockedOrder);

                // 10. 부가 로직을 위한 이벤트 발행 (트랜잭션 커밋 후 비동기 처리)
                Map<Long, Integer> productOrderCounts = new HashMap<>();
                for (var item : orderItems) {
                    productOrderCounts.put(item.getProductId(), item.getQuantity().getValue());
                }

                PaymentCompletedEvent event = new PaymentCompletedEvent(
                        orderId,
                        userId,
                        lockedOrder.getOrderNumber().getValue(),
                        lockedOrder.getTotalAmount().getValue(),
                        paymentMethod,
                        productOrderCounts
                );
                eventPublisher.publishEvent(event);

                return payment;
            });
        });
    }

    public Payment getPayment(String paymentId) {
        return paymentRepository.findById(Long.valueOf(paymentId))
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다"));
    }

    public List<Payment> getPaymentsByOrderId(String orderId) {
        return List.of();
    }
}
