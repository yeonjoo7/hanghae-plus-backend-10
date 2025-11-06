package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.application.service.CouponService.UserCouponInfo;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.DiscountPolicy;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.order.OrderItem;
import com.hanghae.ecommerce.domain.order.OrderState;
import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import com.hanghae.ecommerce.domain.order.repository.OrderRepository;
import com.hanghae.ecommerce.domain.payment.BalanceTransaction;
import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.payment.PaymentState;
import com.hanghae.ecommerce.domain.payment.repository.PaymentRepository;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.user.Point;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 처리 서비스
 * 결제 생성, 처리, 환불 등의 비즈니스 로직을 처리하며 트랜잭션 관리를 포함합니다.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserService userService;
    private final CouponService couponService;
    private final UserRepository userRepository;

    public PaymentService(PaymentRepository paymentRepository,
                         OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         UserService userService,
                         CouponService couponService,
                         UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userService = userService;
        this.couponService = couponService;
        this.userRepository = userRepository;
    }

    /**
     * 주문 결제 처리
     * 
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @param paymentMethod 결제 수단
     * @param couponIds 사용할 쿠폰 ID 목록 (null 가능)
     * @return 결제 결과
     */
    public PaymentResult processPayment(Long userId, Long orderId, PaymentMethod paymentMethod, List<Long> couponIds) {
        // 사용자 존재 및 활성 상태 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));
        
        if (!user.isActive()) {
            throw new IllegalStateException("비활성 사용자는 결제를 진행할 수 없습니다.");
        }

        // 주문 조회 및 검증
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. ID: " + orderId));
        
        // 소유권 확인
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 주문이 아닙니다.");
        }

        // 결제 가능 상태 확인
        if (!order.getState().canBePaid()) {
            throw new IllegalStateException("결제할 수 없는 주문 상태입니다: " + order.getState());
        }

        // 이미 결제된 주문인지 확인
        if (paymentRepository.existsByOrderIdAndState(orderId, PaymentState.COMPLETED)) {
            throw new IllegalStateException("이미 결제가 완료된 주문입니다.");
        }

        // 주문 아이템 조회
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        if (orderItems.isEmpty()) {
            throw new IllegalArgumentException("주문 아이템이 없습니다.");
        }

        // 쿠폰 적용 및 할인 계산
        DiscountResult discountResult = applyCouponsAndCalculateDiscount(userId, order, orderItems, couponIds);

        // 최종 결제 금액 계산
        Money finalAmount = order.getAmount().add(discountResult.getTotalDiscountAmount().multiply(-1));
        if (finalAmount.getAmount() < 0) {
            throw new IllegalArgumentException("결제 금액이 0보다 작을 수 없습니다.");
        }

        // 결제 수단별 처리
        PaymentResult paymentResult;
        switch (paymentMethod) {
            case POINT:
                paymentResult = processPointPayment(userId, order, finalAmount, discountResult);
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 결제 수단입니다: " + paymentMethod);
        }

        return paymentResult;
    }

    /**
     * 포인트 결제 처리
     */
    private PaymentResult processPointPayment(Long userId, Order order, Money finalAmount, DiscountResult discountResult) {
        Point paymentAmount = Point.of(finalAmount.getAmount());
        
        // 잔액 확인
        Point userBalance = userService.getUserBalance(userId);
        if (!userBalance.isGreaterThanOrEqual(paymentAmount)) {
            throw new IllegalArgumentException("잔액이 부족합니다. 현재: " + userBalance.getValue() + 
                                             ", 필요: " + paymentAmount.getValue() + 
                                             ", 부족: " + (paymentAmount.getValue() - userBalance.getValue()));
        }

        try {
            // 결제 생성
            Payment payment = Payment.create(order.getId(), PaymentMethod.POINT, finalAmount, null);
            Payment savedPayment = paymentRepository.save(payment);

            // 포인트 차감
            BalanceTransaction transaction = userService.usePoint(
                    userId, 
                    paymentAmount, 
                    order.getId(), 
                    "주문 결제 (" + order.getOrderNumber().getValue() + ")"
            );

            // 쿠폰 사용 처리
            discountResult.getAppliedCoupons().forEach(coupon -> {
                try {
                    couponService.useCoupon(userId, coupon.getUserCouponId());
                } catch (Exception e) {
                    throw new RuntimeException("쿠폰 사용 처리 중 오류가 발생했습니다: " + e.getMessage());
                }
            });

            // 주문 할인 적용
            if (discountResult.getTotalDiscountAmount().isPositive()) {
                order.applyDiscount(discountResult.getTotalDiscountAmount());
            }

            // 주문 및 주문 아이템 완료 처리
            order.completePayment();
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
            orderItems.forEach(OrderItem::complete);

            orderRepository.save(order);
            orderItemRepository.saveAll(orderItems);

            // 결제 완료 처리
            savedPayment.complete();
            paymentRepository.save(savedPayment);

            return new PaymentResult(
                    savedPayment,
                    order,
                    discountResult.getAppliedCoupons(),
                    new BalanceInfo(
                            transaction.getBalanceBefore(),
                            transaction.getBalanceAfter(),
                            paymentAmount
                    ),
                    true,
                    null
            );

        } catch (Exception e) {
            throw new RuntimeException("결제 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 쿠폰 적용 및 할인 계산
     */
    private DiscountResult applyCouponsAndCalculateDiscount(Long userId, Order order, 
                                                          List<OrderItem> orderItems, List<Long> couponIds) {
        DiscountResult result = new DiscountResult();
        
        if (couponIds == null || couponIds.isEmpty()) {
            return result;
        }

        // 쿠폰 유효성 검증 및 적용
        for (Long couponId : couponIds) {
            UserCouponInfo couponInfo = couponService.getUserCoupon(userId, couponId);
            
            // 쿠폰 사용 가능 여부 확인
            if (!couponInfo.canUse()) {
                throw new IllegalArgumentException("사용할 수 없는 쿠폰입니다. ID: " + couponId);
            }

            Coupon coupon = couponInfo.getCoupon();
            DiscountPolicy discountPolicy = coupon.getDiscountPolicy();

            // 최소 주문 금액 확인
            if (discountPolicy.hasMinOrderAmount() && 
                order.getAmount().getAmount() < discountPolicy.getMinOrderAmount().getValue()) {
                throw new IllegalArgumentException("최소 주문 금액을 만족하지 않습니다. " +
                    "최소: " + discountPolicy.getMinOrderAmount() + ", 주문: " + order.getAmount().getAmount());
            }

            // 할인 금액 계산
            Money discountAmount = calculateDiscountAmount(order, orderItems, discountPolicy);
            
            result.addAppliedCoupon(couponInfo);
            result.addDiscountAmount(discountAmount);
        }

        return result;
    }

    /**
     * 할인 금액 계산
     */
    private Money calculateDiscountAmount(Order order, List<OrderItem> orderItems, DiscountPolicy discountPolicy) {
        switch (discountPolicy.getType()) {
            case ORDER:
                // 장바구니 전체 할인
                return calculateCartDiscount(order.getAmount(), discountPolicy);
            case CART_ITEM:
                // 특정 상품 할인
                return calculateItemDiscount(orderItems, discountPolicy);
            default:
                throw new IllegalArgumentException("지원하지 않는 할인 유형입니다: " + discountPolicy.getType());
        }
    }

    /**
     * 장바구니 전체 할인 계산
     */
    private Money calculateCartDiscount(Money orderAmount, DiscountPolicy discountPolicy) {
        switch (discountPolicy.getDiscountType()) {
            case RATE:
                int discountAmount = (int) (orderAmount.getAmount() * discountPolicy.getDiscountValue() / 100.0);
                return Money.of(discountAmount);
            case AMOUNT:
                return Money.of(discountPolicy.getDiscountValue());
            default:
                throw new IllegalArgumentException("지원하지 않는 할인 방식입니다: " + discountPolicy.getDiscountType());
        }
    }

    /**
     * 특정 상품 할인 계산
     */
    private Money calculateItemDiscount(List<OrderItem> orderItems, DiscountPolicy discountPolicy) {
        List<Long> applicableProductIds = discountPolicy.getApplicableProductIds();
        if (applicableProductIds == null || applicableProductIds.isEmpty()) {
            return Money.zero();
        }

        Money totalItemDiscount = Money.zero();
        for (OrderItem orderItem : orderItems) {
            if (applicableProductIds.contains(orderItem.getProductId())) {
                Money itemAmount = orderItem.getPrice().multiply(orderItem.getQuantity().getValue());
                Money itemDiscount;
                
                switch (discountPolicy.getDiscountType()) {
                    case RATE:
                        int discountAmount = (int) (itemAmount.getAmount() * discountPolicy.getDiscountValue() / 100.0);
                        itemDiscount = Money.of(discountAmount);
                        break;
                    case AMOUNT:
                        itemDiscount = Money.of(discountPolicy.getDiscountValue());
                        break;
                    default:
                        throw new IllegalArgumentException("지원하지 않는 할인 방식입니다: " + discountPolicy.getDiscountType());
                }
                
                totalItemDiscount = totalItemDiscount.add(itemDiscount);
            }
        }
        
        return totalItemDiscount;
    }

    /**
     * 결제 조회
     * 
     * @param userId 사용자 ID
     * @param paymentId 결제 ID
     * @return 결제 정보
     */
    public Payment getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제를 찾을 수 없습니다. ID: " + paymentId));
        
        // 주문을 통한 소유권 확인
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 결제가 아닙니다.");
        }

        return payment;
    }

    /**
     * 결제 환불 처리
     * 
     * @param userId 사용자 ID
     * @param paymentId 결제 ID
     * @return 환불된 결제 정보
     */
    public Payment refundPayment(Long userId, Long paymentId) {
        Payment payment = getPayment(userId, paymentId);
        
        // 환불 가능 상태 확인
        if (!payment.getState().canBeRefunded()) {
            throw new IllegalStateException("환불할 수 없는 결제 상태입니다: " + payment.getState());
        }

        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        try {
            // 결제 수단별 환불 처리
            switch (payment.getMethod()) {
                case POINT:
                    // 포인트 환불
                    Point refundAmount = Point.of(payment.getPaidAmount().getAmount());
                    userService.refundPoint(userId, refundAmount, order.getId());
                    break;
                default:
                    throw new IllegalArgumentException("지원하지 않는 결제 수단입니다: " + payment.getMethod());
            }

            // 결제 환불 상태 변경
            payment.refund();
            paymentRepository.save(payment);

            // 주문 환불 상태 변경
            order.refund();
            orderRepository.save(order);

            return payment;

        } catch (Exception e) {
            throw new RuntimeException("환불 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 할인 결과를 담는 클래스
     */
    private static class DiscountResult {
        private final List<UserCouponInfo> appliedCoupons = new java.util.ArrayList<>();
        private Money totalDiscountAmount = Money.zero();

        public void addAppliedCoupon(UserCouponInfo coupon) {
            appliedCoupons.add(coupon);
        }

        public void addDiscountAmount(Money amount) {
            totalDiscountAmount = totalDiscountAmount.add(amount);
        }

        public List<UserCouponInfo> getAppliedCoupons() {
            return appliedCoupons;
        }

        public Money getTotalDiscountAmount() {
            return totalDiscountAmount;
        }
    }

    /**
     * 결제 결과를 담는 클래스
     */
    public static class PaymentResult {
        private final Payment payment;
        private final Order order;
        private final List<UserCouponInfo> appliedCoupons;
        private final BalanceInfo balanceInfo;
        private final boolean success;
        private final String errorMessage;

        public PaymentResult(Payment payment, Order order, List<UserCouponInfo> appliedCoupons, 
                           BalanceInfo balanceInfo, boolean success, String errorMessage) {
            this.payment = payment;
            this.order = order;
            this.appliedCoupons = appliedCoupons;
            this.balanceInfo = balanceInfo;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public Payment getPayment() {
            return payment;
        }

        public Order getOrder() {
            return order;
        }

        public List<UserCouponInfo> getAppliedCoupons() {
            return appliedCoupons;
        }

        public BalanceInfo getBalanceInfo() {
            return balanceInfo;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Long getPaymentId() {
            return payment != null ? payment.getId() : null;
        }

        public String getOrderNumber() {
            return order != null ? order.getOrderNumber().getValue() : null;
        }

        public Money getFinalAmount() {
            return payment != null ? payment.getPaidAmount() : Money.zero();
        }
    }

    /**
     * 잔액 정보를 담는 클래스
     */
    public static class BalanceInfo {
        private final Point before;
        private final Point after;
        private final Point used;

        public BalanceInfo(Point before, Point after, Point used) {
            this.before = before;
            this.after = after;
            this.used = used;
        }

        public Point getBefore() {
            return before;
        }

        public Point getAfter() {
            return after;
        }

        public Point getUsed() {
            return used;
        }
    }
}