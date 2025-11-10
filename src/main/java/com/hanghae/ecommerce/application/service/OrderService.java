package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.application.service.CartService.CartItemInfo;
import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartItem;
import com.hanghae.ecommerce.domain.cart.repository.CartRepository;
import com.hanghae.ecommerce.domain.order.Address;
import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.order.OrderItem;
import com.hanghae.ecommerce.domain.order.OrderState;
import com.hanghae.ecommerce.domain.order.Recipient;
import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import com.hanghae.ecommerce.domain.order.repository.OrderRepository;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문 관리 서비스
 * 주문 생성, 조회, 취소 등의 비즈니스 로직을 처리합니다.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final StockService stockService;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PopularProductService popularProductService;

    public OrderService(OrderRepository orderRepository,
                       OrderItemRepository orderItemRepository,
                       CartService cartService,
                       StockService stockService,
                       UserRepository userRepository,
                       CartRepository cartRepository,
                       PopularProductService popularProductService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartService = cartService;
        this.stockService = stockService;
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.popularProductService = popularProductService;
    }

    /**
     * 주문 생성
     * 
     * @param userId 사용자 ID
     * @param cartItemIds 주문할 장바구니 아이템 ID 목록
     * @param recipientName 수령인 이름
     * @param phone 연락처
     * @param zipCode 우편번호
     * @param address 주소
     * @param detailAddress 상세 주소
     * @return 생성된 주문 정보
     */
    public OrderInfo createOrder(Long userId, List<Long> cartItemIds, 
                                String recipientName, String phone, String zipCode, 
                                String address, String detailAddress) {
        // 사용자 존재 및 활성 상태 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));
        
        if (!user.isActive()) {
            throw new IllegalStateException("비활성 사용자는 주문을 생성할 수 없습니다.");
        }

        // 장바구니 아이템 조회 및 검증
        List<CartItemInfo> cartItemInfos = cartService.getSelectedCartItems(userId, cartItemIds);
        if (cartItemInfos.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품이 없습니다.");
        }

        // 재고 확인
        Map<Long, Integer> stockRequests = cartItemInfos.stream()
                .collect(Collectors.toMap(
                    item -> item.getProductId(),
                    CartItemInfo::getQuantity,
                    Integer::sum
                ));

        StockService.StockCheckResult stockCheckResult = stockService.checkStockAvailability(stockRequests);
        if (!stockCheckResult.isAllStockAvailable()) {
            throw new IllegalArgumentException("재고가 부족한 상품이 있습니다: " + stockCheckResult.getShortages());
        }

        // 총 주문 금액 계산
        Money totalAmount = cartItemInfos.stream()
                .map(CartItemInfo::getSubtotal)
                .reduce(Money.zero(), Money::add);

        // 배송지 정보 생성
        Recipient recipient = Recipient.of(recipientName, phone);
        Address deliveryAddress = Address.of(zipCode, address, detailAddress);

        // 장바구니 조회 (쿠폰 정보를 위해)
        Cart cart = cartService.getOrCreateActiveCart(userId);

        // 주문 생성
        Order order = Order.create(
                userId, 
                cart.getUserCouponId(), 
                cart.getId(), 
                totalAmount, 
                Money.zero(), 
                recipient, 
                deliveryAddress
        );
        Order savedOrder = orderRepository.save(order);

        // 주문 아이템 생성
        List<OrderItem> orderItems = cartItemInfos.stream()
                .map(itemInfo -> OrderItem.createForProduct(
                        savedOrder.getId(),
                        itemInfo.getProductId(),
                        itemInfo.getPrice(),
                        Quantity.of(itemInfo.getQuantity()),
                        Money.zero()
                ))
                .collect(Collectors.toList());

        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        // 재고 차감
        stockService.reduceStocks(stockRequests);

        // 주문 정보 반환
        return createOrderInfo(savedOrder, savedOrderItems, cartItemInfos);
    }

    /**
     * 주문 조회
     * 
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @return 주문 정보
     */
    public OrderInfo getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. ID: " + orderId));
        
        // 소유권 확인
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 주문이 아닙니다.");
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        return createOrderInfoFromOrder(order, orderItems);
    }

    /**
     * 사용자 주문 목록 조회
     * 
     * @param userId 사용자 ID
     * @return 주문 목록
     */
    public List<OrderSummary> getUserOrders(Long userId) {
        // 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        
        return orders.stream()
                .map(this::createOrderSummary)
                .collect(Collectors.toList());
    }

    /**
     * 주문 목록 조회 (필터링 지원)
     * 
     * @param userId 사용자 ID
     * @param status 주문 상태 필터 (null이면 전체 조회)
     * @param startDate 조회 시작일 (null이면 제한 없음)
     * @param endDate 조회 종료일 (null이면 제한 없음)
     * @return 필터링된 주문 목록
     */
    public List<OrderSummary> getUserOrdersWithFilter(Long userId, OrderState status, 
                                                     LocalDateTime startDate, LocalDateTime endDate) {
        // 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        List<Order> orders;
        if (status != null && startDate != null && endDate != null) {
            orders = orderRepository.findByUserIdAndStateAndCreatedAtBetweenOrderByCreatedAtDesc(
                    userId, status, startDate, endDate);
        } else if (status != null) {
            orders = orderRepository.findByUserIdAndStateOrderByCreatedAtDesc(userId, status);
        } else if (startDate != null && endDate != null) {
            orders = orderRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    userId, startDate, endDate);
        } else {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        
        return orders.stream()
                .map(this::createOrderSummary)
                .collect(Collectors.toList());
    }

    /**
     * 주문 취소
     * 
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @return 취소된 주문 정보
     */
    public OrderInfo cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. ID: " + orderId));
        
        // 소유권 확인
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 주문이 아닙니다.");
        }

        // 주문 취소 가능 여부 확인
        if (!order.getState().canBeCancelled()) {
            throw new IllegalStateException("취소할 수 없는 주문 상태입니다: " + order.getState());
        }

        // 주문 아이템 조회
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

        // 재고 복원
        Map<Long, Integer> stockRestorations = orderItems.stream()
                .collect(Collectors.toMap(
                    OrderItem::getProductId,
                    item -> item.getQuantity().getValue(),
                    Integer::sum
                ));
        
        stockService.restoreStocks(stockRestorations);

        // 주문 및 주문 아이템 취소
        order.cancel();
        orderItems.forEach(OrderItem::cancel);
        
        orderRepository.save(order);
        orderItemRepository.saveAll(orderItems);

        return createOrderInfoFromOrder(order, orderItems);
    }

    /**
     * 배송지 정보 수정
     * 
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @param recipientName 수령인 이름
     * @param phone 연락처
     * @param zipCode 우편번호
     * @param address 주소
     * @param detailAddress 상세 주소
     * @return 수정된 주문 정보
     */
    public OrderInfo updateDeliveryInfo(Long userId, Long orderId, String recipientName, 
                                       String phone, String zipCode, String address, String detailAddress) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. ID: " + orderId));
        
        // 소유권 확인
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 주문이 아닙니다.");
        }

        // 배송지 정보 생성
        Recipient recipient = Recipient.of(recipientName, phone);
        Address deliveryAddress = Address.of(zipCode, address, detailAddress);

        // 배송지 정보 수정
        order.updateDeliveryInfo(recipient, deliveryAddress);
        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        return createOrderInfoFromOrder(savedOrder, orderItems);
    }

    /**
     * 완료된 주문 조회 (결제 완료 후 호출)
     * 
     * @param orderId 주문 ID
     * @return 주문 정보
     */
    public OrderInfo getCompletedOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. ID: " + orderId));
        
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        return createOrderInfoFromOrder(order, orderItems);
    }

    /**
     * 주문 정보 생성 (장바구니 아이템 정보 포함)
     */
    private OrderInfo createOrderInfo(Order order, List<OrderItem> orderItems, List<CartItemInfo> cartItemInfos) {
        Map<Long, CartItemInfo> cartItemMap = cartItemInfos.stream()
                .collect(Collectors.toMap(CartItemInfo::getProductId, Function.identity()));

        List<OrderItemInfo> orderItemInfos = orderItems.stream()
                .map(orderItem -> {
                    CartItemInfo cartItemInfo = cartItemMap.get(orderItem.getProductId());
                    if (cartItemInfo == null) {
                        throw new IllegalStateException("장바구니 아이템 정보를 찾을 수 없습니다.");
                    }
                    return new OrderItemInfo(orderItem, cartItemInfo.getProduct(), cartItemInfo.getProduct().getName());
                })
                .collect(Collectors.toList());

        return new OrderInfo(order, orderItemInfos);
    }

    /**
     * 주문 정보 생성 (DB에서 조회한 데이터)
     */
    private OrderInfo createOrderInfoFromOrder(Order order, List<OrderItem> orderItems) {
        // 상품 정보 조회가 필요한 경우를 위한 기본 구현
        List<OrderItemInfo> orderItemInfos = orderItems.stream()
                .map(orderItem -> new OrderItemInfo(orderItem, null, "상품명 조회 필요"))
                .collect(Collectors.toList());

        return new OrderInfo(order, orderItemInfos);
    }

    /**
     * 주문 요약 정보 생성
     */
    private OrderSummary createOrderSummary(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        int itemCount = orderItems.stream()
                .mapToInt(item -> item.getQuantity().getValue())
                .sum();
        
        return new OrderSummary(
                order.getId(),
                order.getOrderNumber().getValue(),
                order.getState(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getAmount(),
                itemCount,
                order.getCreatedAt()
        );
    }

    /**
     * 주문 전체 정보를 담는 클래스
     */
    public static class OrderInfo {
        private final Order order;
        private final List<OrderItemInfo> orderItems;

        public OrderInfo(Order order, List<OrderItemInfo> orderItems) {
            this.order = order;
            this.orderItems = orderItems;
        }

        public Order getOrder() {
            return order;
        }

        public List<OrderItemInfo> getOrderItems() {
            return orderItems;
        }

        public Long getOrderId() {
            return order.getId();
        }

        public String getOrderNumber() {
            return order.getOrderNumber().getValue();
        }

        public OrderState getStatus() {
            return order.getState();
        }

        public Money getTotalAmount() {
            return order.getTotalAmount();
        }

        public int getItemCount() {
            return orderItems.size();
        }
    }

    /**
     * 주문 아이템 정보를 담는 클래스
     */
    public static class OrderItemInfo {
        private final OrderItem orderItem;
        private final Product product;
        private final String productName;

        public OrderItemInfo(OrderItem orderItem, Product product, String productName) {
            this.orderItem = orderItem;
            this.product = product;
            this.productName = productName;
        }

        public OrderItem getOrderItem() {
            return orderItem;
        }

        public Product getProduct() {
            return product;
        }

        public Long getOrderItemId() {
            return orderItem.getId();
        }

        public Long getProductId() {
            return orderItem.getProductId();
        }

        public String getProductName() {
            return productName;
        }

        public Money getPrice() {
            return orderItem.getPrice();
        }

        public int getQuantity() {
            return orderItem.getQuantity().getValue();
        }

        public Money getSubtotal() {
            return orderItem.getTotalAmount();
        }
    }

    /**
     * 주문 요약 정보를 담는 클래스
     */
    public static class OrderSummary {
        private final Long orderId;
        private final String orderNumber;
        private final OrderState status;
        private final Money totalAmount;
        private final Money discountAmount;
        private final Money finalAmount;
        private final int itemCount;
        private final LocalDateTime createdAt;

        public OrderSummary(Long orderId, String orderNumber, OrderState status, 
                          Money totalAmount, Money discountAmount, Money finalAmount,
                          int itemCount, LocalDateTime createdAt) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.status = status;
            this.totalAmount = totalAmount;
            this.discountAmount = discountAmount;
            this.finalAmount = finalAmount;
            this.itemCount = itemCount;
            this.createdAt = createdAt;
        }

        public Long getOrderId() {
            return orderId;
        }

        public String getOrderNumber() {
            return orderNumber;
        }

        public OrderState getStatus() {
            return status;
        }

        public Money getTotalAmount() {
            return totalAmount;
        }

        public Money getDiscountAmount() {
            return discountAmount;
        }

        public Money getFinalAmount() {
            return finalAmount;
        }

        public int getItemCount() {
            return itemCount;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}