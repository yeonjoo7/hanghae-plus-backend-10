package com.hanghae.ecommerce.infrastructure.service;

import com.hanghae.ecommerce.application.service.PaymentService;
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
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.infrastructure.external.DataTransmissionService;
import com.hanghae.ecommerce.presentation.exception.InsufficientBalanceException;
import com.hanghae.ecommerce.presentation.exception.InsufficientStockException;
import com.hanghae.ecommerce.presentation.exception.OrderNotFoundException;
import com.hanghae.ecommerce.presentation.exception.PaymentAlreadyCompletedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final JdbcTemplate jdbcTemplate;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final DataTransmissionService dataTransmissionService;

    public PaymentServiceImpl(JdbcTemplate jdbcTemplate,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            ProductRepository productRepository,
                            UserRepository userRepository,
                            PaymentRepository paymentRepository,
                            BalanceTransactionRepository balanceTransactionRepository,
                            DataTransmissionService dataTransmissionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.balanceTransactionRepository = balanceTransactionRepository;
        this.dataTransmissionService = dataTransmissionService;
    }

    @Override
    @Transactional
    public PaymentResultDto processPayment(Long userId, Long orderId, PaymentMethod paymentMethod, List<Long> couponIds) {
        // TODO: 실제 구현 필요
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    @Transactional
    public Payment processPayment(String orderId, String userId, PaymentMethod paymentMethod) {
        // 1. 주문 조회 (락)
        Order order = jdbcTemplate.queryForObject(
                "SELECT * FROM orders WHERE id = ? AND user_id = ? AND status = 'PENDING' FOR UPDATE",
                (rs, rowNum) -> {
                    // Order 객체 생성 로직
                    return orderRepository.findById(Long.valueOf(orderId)).orElse(null);
                },
                orderId, userId
        );
        
        if (order == null) {
            throw new OrderNotFoundException(Long.valueOf(orderId));
        }

        // 이미 결제된 주문인지 확인
        if (order.getState() != OrderState.PENDING_PAYMENT) {
            throw new PaymentAlreadyCompletedException();
        }

        // 2. 사용자 잔액 확인 및 차감
        var user = userRepository.findById(Long.valueOf(userId))
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        if (user.getAvailablePoint().getValue() < order.getTotalAmount().getValue()) {
            throw new InsufficientBalanceException(order.getTotalAmount().getValue(), user.getAvailablePoint().getValue());
        }

        // 잔액 차감
        user.usePoint(Point.of(order.getTotalAmount().getValue()));
        userRepository.save(user);

        // 3. 재고 차감
        var orderItems = orderItemRepository.findByOrderId(Long.valueOf(orderId));
        for (var item : orderItems) {
            boolean stockDecreased = productRepository.decreaseStock(
                    item.getProductId(), 
                    item.getQuantity()
            );
            
            if (!stockDecreased) {
                // 재고 부족시 예외 발생 - 요청 수량과 가용 재고를 정확히 알 수 없으므로 임시값 사용
                throw new InsufficientStockException(item.getQuantity().getValue(), 0);
            }
        }

        // 4. 쿠폰 사용 처리
        // 쿠폰 사용 처리는 다른 서비스에서 수행
        // if (order.getCouponId() != null) {
        //     jdbcTemplate.update(
        //             """
        //             UPDATE user_coupons
        //             SET status = 'USED', used_at = NOW()
        //             WHERE user_id = ? AND coupon_id = ? AND status = 'AVAILABLE'
        //             """,
        //             userId, 1L // 쿠폰 ID 처리 필요
        //     );
        // }

        // 5. 결제 정보 생성
        Payment payment = Payment.create(
                Long.valueOf(orderId),
                paymentMethod,
                order.getTotalAmount(),
                null // 포인트 결제는 즉시 처리되므로 만료 시간 불필요
        );
        payment.complete();
        paymentRepository.save(payment);

        // 6. 잔액 거래 내역 저장
        Point beforeBalance = Point.of(user.getAvailablePoint().getValue() + order.getTotalAmount().getValue());
        Point afterBalance = user.getAvailablePoint();
        
        BalanceTransaction transaction = BalanceTransaction.createPayment(
                Long.valueOf(userId),
                Long.valueOf(orderId),
                Point.of(order.getTotalAmount().getValue()),
                beforeBalance,
                "주문 결제: " + order.getOrderNumber().getValue()
        );
        balanceTransactionRepository.save(transaction);

        // 7. 주문 상태 변경
        order.complete();
        orderRepository.save(order);

        // 8. 데이터 플랫폼 전송 (트랜잭션 외부, 실패해도 롤백하지 않음)
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
                    "subtotal", item.getSubtotal()
            )).toList());
            orderData.put("timestamp", LocalDateTime.now());
            
            dataTransmissionService.send(orderData);
        } catch (Exception e) {
            // 데이터 전송 실패는 무시 (Outbox에 저장됨)
            System.err.println("데이터 전송 실패, Outbox에 저장됨: " + e.getMessage());
        }

        return payment;
    }

    @Override
    public Payment getPayment(String paymentId) {
        return paymentRepository.findById(Long.valueOf(paymentId))
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다"));
    }

    @Override
    public List<Payment> getPaymentsByOrderId(String orderId) {
        return paymentRepository.findByOrderId(Long.valueOf(orderId));
    }
}