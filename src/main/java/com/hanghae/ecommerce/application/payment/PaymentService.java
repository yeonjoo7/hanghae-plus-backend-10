package com.hanghae.ecommerce.application.payment;

import com.hanghae.ecommerce.presentation.dto.PaymentResultDto;
import com.hanghae.ecommerce.presentation.dto.UserCouponDto;
import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.order.OrderState;
import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import com.hanghae.ecommerce.domain.order.repository.OrderRepository;
import com.hanghae.ecommerce.domain.payment.BalanceTransaction;
import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.payment.PaymentState;
import com.hanghae.ecommerce.domain.payment.TransactionType;
import com.hanghae.ecommerce.domain.payment.repository.BalanceTransactionRepository;
import com.hanghae.ecommerce.domain.payment.repository.PaymentRepository;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.user.Point;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.infrastructure.external.DataTransmissionService;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import com.hanghae.ecommerce.presentation.exception.InsufficientBalanceException;
import com.hanghae.ecommerce.presentation.exception.InsufficientStockException;
import com.hanghae.ecommerce.presentation.exception.OrderNotFoundException;
import com.hanghae.ecommerce.presentation.exception.PaymentAlreadyCompletedException;
import com.hanghae.ecommerce.application.product.StockService;
import com.hanghae.ecommerce.application.coupon.CouponService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 결제 처리 서비스
 * 
 * 분산락을 사용하여 동시 결제 요청에 대한 동시성 제어를 제공합니다.
 * - 사용자별 잔액 차감: balance:{userId} 락
 * - 주문별 결제 처리: payment:{orderId} 락
 */
@Service
public class PaymentService {

    private final JdbcTemplate jdbcTemplate;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final DataTransmissionService dataTransmissionService;
    private final StockService stockService;
    private final CouponService couponService;
    private final LockManager lockManager;
    private final PlatformTransactionManager transactionManager;

    public PaymentService(JdbcTemplate jdbcTemplate,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            BalanceTransactionRepository balanceTransactionRepository,
            DataTransmissionService dataTransmissionService,
            StockService stockService,
            CouponService couponService,
            LockManager lockManager,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.balanceTransactionRepository = balanceTransactionRepository;
        this.dataTransmissionService = dataTransmissionService;
        this.stockService = stockService;
        this.couponService = couponService;
        this.lockManager = lockManager;
        this.transactionManager = transactionManager;
    }

    public PaymentResultDto processPayment(Long userId, Long orderId, PaymentMethod paymentMethod,
            List<Long> couponIds) {
        // 기존 구현된 메서드 호출
        Payment payment = processPayment(String.valueOf(orderId), String.valueOf(userId), paymentMethod);

        // PaymentResultDto로 변환하여 반환
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return new PaymentResultDto(
                payment,
                order,
                List.of(), // appliedCoupons - 현재 구현에서는 빈 리스트
                true, // success
                null // errorMessage
        );
    }

    /**
     * 결제 처리 (분산락 적용)
     * 
     * 동시 결제 요청에 대한 동시성 제어:
     * - payment:{orderId} 락: 동일 주문에 대한 중복 결제 방지
     * - balance:{userId} 락: 사용자 잔액 동시 차감 방지
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

        // 2. 주문별 결제 락 획득 - 동일 주문에 대한 중복 결제 방지
        String paymentLockKey = "payment:" + orderId;

        return lockManager.executeWithLock(paymentLockKey, () -> {
            // 3. 사용자별 잔액 락 획득 - 동시 잔액 차감 방지
            String balanceLockKey = "balance:" + userId;

            return lockManager.executeWithLock(balanceLockKey, () -> {
                TransactionTemplate template = new TransactionTemplate(transactionManager);
                template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                return template.execute(status -> {
                    // 4. 주문 상태 재확인 (락 획득 후 - Race Condition 방지)
                    Order lockedOrder = orderRepository.findById(Long.valueOf(orderId))
                            .orElseThrow(() -> new OrderNotFoundException(Long.valueOf(orderId)));

                    if (lockedOrder.getState() != OrderState.PENDING_PAYMENT) {
                        throw new PaymentAlreadyCompletedException();
                    }

                    // 5. 사용자 잔액 확인 및 차감
                    User user = userRepository.findById(Long.valueOf(userId))
                            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

                    if (user.getAvailablePoint().getValue() < lockedOrder.getTotalAmount().getValue()) {
                        throw new InsufficientBalanceException(lockedOrder.getTotalAmount().getValue(),
                                user.getAvailablePoint().getValue());
                    }

                    // 잔액 차감 전 값 저장
                    Point beforeBalance = user.getAvailablePoint();

                    // 잔액 차감
                    user.usePoint(Point.of(lockedOrder.getTotalAmount().getValue()));
                    userRepository.save(user);

                    // 6. 재고 차감 (StockService 내부에서 분산락 사용)
                    var orderItems = orderItemRepository.findByOrderId(Long.valueOf(orderId));
                    Map<Long, Integer> stockReductions = new HashMap<>();
                    for (var item : orderItems) {
                        stockReductions.put(item.getProductId(), item.getQuantity().getValue());
                    }

                    try {
                        stockService.reduceStocks(stockReductions);
                    } catch (IllegalArgumentException e) {
                        // 재고 부족 시 예외 변환
                        throw new InsufficientStockException(0, 0);
                    }

                    // 7. 쿠폰 사용 처리
                    if (lockedOrder.getUserCouponId() != null) {
                        couponService.useCoupon(lockedOrder.getUserCouponId(), Long.valueOf(userId));
                    }

                    // 8. 결제 정보 생성
                    Payment payment = Payment.create(
                            Long.valueOf(orderId),
                            paymentMethod,
                            lockedOrder.getTotalAmount(),
                            null // 포인트 결제는 즉시 처리되므로 만료 시간 불필요
                    );
                    payment.complete();
                    paymentRepository.save(payment);

                    // 9. 잔액 거래 내역 저장
                    Point afterBalance = user.getAvailablePoint();

                    BalanceTransaction transaction = BalanceTransaction.createPayment(
                            Long.valueOf(userId),
                            Long.valueOf(orderId),
                            Point.of(lockedOrder.getTotalAmount().getValue()),
                            beforeBalance,
                            "주문 결제: " + lockedOrder.getOrderNumber().getValue());
                    balanceTransactionRepository.save(transaction);

                    // 10. 주문 상태 변경
                    lockedOrder.complete();
                    orderRepository.save(lockedOrder);

                    // 11. 데이터 플랫폼 전송 (실패해도 롤백하지 않음)
                    sendOrderDataAsync(orderId, userId, lockedOrder, orderItems, paymentMethod);

                    return payment;
                });
            });
        });
    }

    /**
     * 주문 데이터를 데이터 플랫폼으로 비동기 전송
     */
    private void sendOrderDataAsync(String orderId, String userId, Order order,
            List<com.hanghae.ecommerce.domain.order.OrderItem> orderItems, PaymentMethod paymentMethod) {
        try {
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderId", orderId);
            orderData.put("userId", userId);
            orderData.put("orderNumber", order.getOrderNumber().getValue());
            orderData.put("totalAmount", order.getTotalAmount().getValue());
            orderData.put("discountAmount", 0);
            orderData.put("finalAmount", order.getTotalAmount().getValue());
            orderData.put("paymentMethod", paymentMethod.name());
            orderData.put("orderItems", orderItems.stream().map(item -> Map.of(
                    "productId", item.getProductId(),
                    "quantity", item.getQuantity(),
                    "unitPrice", item.getUnitPrice(),
                    "subtotal", item.getSubtotal())).toList());
            orderData.put("timestamp", LocalDateTime.now());

            dataTransmissionService.send(orderData);
        } catch (Exception e) {
            // 데이터 전송 실패는 무시 (Outbox에 저장됨)
            System.err.println("데이터 전송 실패, Outbox에 저장됨: " + e.getMessage());
        }
    }

    public Payment getPayment(String paymentId) {
        return paymentRepository.findById(Long.valueOf(paymentId))
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다"));
    }

    public List<Payment> getPaymentsByOrderId(String orderId) {
        // return paymentRepository.findByOrderId(Long.valueOf(orderId));
        return List.of();
    }
}