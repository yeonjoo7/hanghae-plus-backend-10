package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartItem;
import com.hanghae.ecommerce.domain.cart.CartState;
import com.hanghae.ecommerce.domain.cart.repository.CartRepository;

import java.time.LocalDateTime;
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
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartService cartService;

    @Mock
    private StockService stockService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private PopularProductService popularProductService;

    @InjectMocks
    private OrderService orderService;

    private User testUser;
    private Product testProduct;
    private Cart testCart;
    private CartItem testCartItem;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", "테스트", "010-1234-5678");
        
        testProduct = Product.create(
            "테스트 상품",
            "테스트 상품 설명",
            Money.of(10000),
            Quantity.of(5)
        );

        testCart = Cart.restore(1L, 1L, null, CartState.NORMAL, LocalDateTime.now(), LocalDateTime.now());
        testCartItem = CartItem.createForProduct(testCart.getId(), 1L, Quantity.of(2));
    }

    @Test
    @DisplayName("주문 생성 성공")
    void createOrder_Success() {
        // given
        Long userId = 1L;
        List<Long> cartItemIds = List.of(1L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(cartService.getSelectedCartItems(userId, cartItemIds)).thenReturn(
            List.of(new CartService.CartItemInfo(testCartItem, testProduct))
        );
        when(stockService.checkStockAvailability(anyMap())).thenReturn(
            new StockService.StockCheckResult(true, null)
        );
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        OrderService.OrderInfo result = orderService.createOrder(
            userId, cartItemIds, 
            "수령인", "010-9876-5432",
            "12345", "서울시", "상세주소"
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder().getUserId()).isEqualTo(userId);
        assertThat(result.getStatus()).isEqualTo(OrderState.PENDING_PAYMENT);
        assertThat(result.getTotalAmount()).isEqualTo(Money.of(20000)); // 10000 * 2개

        verify(orderRepository).save(any(Order.class));
        verify(orderItemRepository).saveAll(anyList());
        verify(stockService).reduceStocks(anyMap());
    }

    @Test
    @DisplayName("빈 장바구니로 주문 생성 실패")
    void createOrder_EmptyCartItems() {
        // given
        Long userId = 1L;
        List<Long> cartItemIds = List.of();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(cartService.getSelectedCartItems(userId, cartItemIds)).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(
                userId, cartItemIds, 
                "수령인", "010-9876-5432",
                "12345", "서울시", "상세주소"
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("주문할 상품이 없습니다");
    }

    @Test
    @DisplayName("재고 부족으로 주문 생성 실패")
    void createOrder_InsufficientStock() {
        // given
        Long userId = 1L;
        List<Long> cartItemIds = List.of(1L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(cartService.getSelectedCartItems(userId, cartItemIds)).thenReturn(
            List.of(new CartService.CartItemInfo(testCartItem, testProduct))
        );
        when(stockService.checkStockAvailability(anyMap())).thenReturn(
            new StockService.StockCheckResult(false, List.of(
                new StockService.StockShortage(1L, 2, 1)
            ))
        );

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(
                userId, cartItemIds, 
                "수령인", "010-9876-5432",
                "12345", "서울시", "상세주소"
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("재고가 부족한 상품이 있습니다");
    }

    @Test
    @DisplayName("주문 조회 성공")
    void getOrder_Success() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        
        Order order = Order.create(userId, null, null, Money.of(20000), Money.zero(), 
                                  Recipient.of("수령인", "010-9876-5432"), 
                                  Address.of("12345", "서울시", "상세주소"));
        
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        // when
        OrderService.OrderInfo result = orderService.getOrder(userId, orderId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrder().getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회 실패")
    void getOrder_NotFound() {
        // given
        Long userId = 1L;
        Long orderId = 1L;

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.getOrder(userId, orderId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("주문을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("다른 사용자 주문 조회 실패")
    void getOrder_NotOwner() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        Long otherUserId = 2L;
        
        Order order = Order.create(otherUserId, null, null, Money.of(20000), Money.zero(),
                                  Recipient.of("수령인", "010-9876-5432"), 
                                  Address.of("12345", "서울시", "상세주소"));
        
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.getOrder(userId, orderId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("본인의 주문이 아닙니다");
    }

    @Test
    @DisplayName("사용자 주문 목록 조회 성공")
    void getUserOrders_Success() {
        // given
        Long userId = 1L;
        List<Order> orders = List.of(
            Order.create(userId, null, null, Money.of(10000), Money.zero(),
                        Recipient.of("수령인1", "010-1111-1111"), 
                        Address.of("12345", "서울시", "주소1")),
            Order.create(userId, null, null, Money.of(20000), Money.zero(),
                        Recipient.of("수령인2", "010-2222-2222"), 
                        Address.of("54321", "부산시", "주소2"))
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(orders);
        when(orderItemRepository.findByOrderId(any())).thenReturn(List.of());

        // when
        List<OrderService.OrderSummary> result = orderService.getUserOrders(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(orderSummary -> orderSummary.getOrderId() != null);
    }


    @Test
    @DisplayName("주문 취소 성공")
    void cancelOrder_Success() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        
        Order order = Order.create(userId, null, null, Money.of(20000), Money.zero(),
                                  Recipient.of("수령인", "010-9876-5432"), 
                                  Address.of("12345", "서울시", "상세주소"));
        List<OrderItem> orderItems = List.of(
            OrderItem.createForProduct(orderId, 1L, Money.of(10000), Quantity.of(2), Money.zero())
        );

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        orderService.cancelOrder(userId, orderId);

        // then
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        verify(stockService).restoreStock(1L, 2); // 재고 복원
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("이미 결제 완료된 주문 취소 실패")
    void cancelOrder_AlreadyCompleted() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        
        Order order = Order.create(userId, null, null, Money.of(20000), Money.zero(),
                                  Recipient.of("수령인", "010-9876-5432"), 
                                  Address.of("12345", "서울시", "상세주소"));
        order.completePayment(); // 결제 완료 상태로 변경

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("취소할 수 없는 주문 상태입니다");
    }
}