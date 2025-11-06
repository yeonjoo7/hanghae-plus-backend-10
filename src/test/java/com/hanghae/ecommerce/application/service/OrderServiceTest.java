package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartItem;
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
import com.hanghae.ecommerce.domain.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    private UserService userService;

    @InjectMocks
    private OrderService orderService;

    private User testUser;
    private Product testProduct;
    private Cart testCart;
    private CartItem testCartItem;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", UserType.CUSTOMER, "테스트", "010-1234-5678");
        
        testProduct = Product.create(
            "테스트 상품",
            "테스트 상품 설명",
            Money.of(10000),
            Quantity.of(5)
        );

        testCart = Cart.create(1L);
        testCartItem = CartItem.create(testCart.getId(), 1L, null, Quantity.of(2), Money.of(10000));
    }

    @Test
    @DisplayName("주문 생성 성공")
    void createOrder_Success() {
        // given
        Long userId = 1L;
        List<Long> cartItemIds = List.of(1L);
        Recipient recipient = Recipient.create("수령인", "010-9876-5432");
        Address address = Address.create("12345", "서울시", "상세주소");

        when(userService.getUser(userId)).thenReturn(testUser);
        when(cartService.getCartItems(cartItemIds)).thenReturn(List.of(testCartItem));
        when(stockService.getStock(1L)).thenReturn(Stock.create(1L, null, Quantity.of(100)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Order result = orderService.createOrder(userId, cartItemIds, recipient, address);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getState()).isEqualTo(OrderState.PENDING);
        assertThat(result.getAmount().getAmount()).isEqualTo(20000); // 10000 * 2개

        verify(orderRepository).save(any(Order.class));
        verify(orderItemRepository).saveAll(anyList());
        verify(stockService).reduceStock(1L, 2);
        verify(cartService).removeCartItems(cartItemIds);
    }

    @Test
    @DisplayName("빈 장바구니로 주문 생성 실패")
    void createOrder_EmptyCartItems() {
        // given
        Long userId = 1L;
        List<Long> cartItemIds = List.of();
        Recipient recipient = Recipient.create("수령인", "010-9876-5432");
        Address address = Address.create("12345", "서울시", "상세주소");

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, cartItemIds, recipient, address))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("주문할 상품이 없습니다");
    }

    @Test
    @DisplayName("재고 부족으로 주문 생성 실패")
    void createOrder_InsufficientStock() {
        // given
        Long userId = 1L;
        List<Long> cartItemIds = List.of(1L);
        Recipient recipient = Recipient.create("수령인", "010-9876-5432");
        Address address = Address.create("12345", "서울시", "상세주소");

        when(userService.getUser(userId)).thenReturn(testUser);
        when(cartService.getCartItems(cartItemIds)).thenReturn(List.of(testCartItem));
        when(stockService.getStock(1L)).thenReturn(Stock.create(1L, null, Quantity.of(1))); // 재고 부족
        doThrow(new IllegalStateException("재고가 부족합니다"))
            .when(stockService).reduceStock(1L, 2);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, cartItemIds, recipient, address))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("주문 조회 성공")
    void getOrder_Success() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        
        Order order = Order.create(userId, Recipient.create("수령인", "010-9876-5432"), 
                                 Address.create("12345", "서울시", "상세주소"));
        
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // when
        Order result = orderService.getOrder(userId, orderId);

        // then
        assertThat(result).isEqualTo(order);
        assertThat(result.getUserId()).isEqualTo(userId);
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
        
        Order order = Order.create(otherUserId, Recipient.create("수령인", "010-9876-5432"), 
                                 Address.create("12345", "서울시", "상세주소"));
        
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
            Order.create(userId, Recipient.create("수령인1", "010-1111-1111"), 
                        Address.create("12345", "서울시", "주소1")),
            Order.create(userId, Recipient.create("수령인2", "010-2222-2222"), 
                        Address.create("54321", "부산시", "주소2"))
        );

        when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(orders);

        // when
        List<Order> result = orderService.getUserOrders(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(order -> order.getUserId().equals(userId));
    }

    @Test
    @DisplayName("주문 아이템 조회 성공")
    void getOrderItems_Success() {
        // given
        Long orderId = 1L;
        List<OrderItem> orderItems = List.of(
            OrderItem.create(orderId, 1L, null, Quantity.of(2), Money.of(10000)),
            OrderItem.create(orderId, 2L, null, Quantity.of(1), Money.of(20000))
        );

        when(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems);

        // when
        List<OrderItem> result = orderService.getOrderItems(orderId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(item -> item.getOrderId().equals(orderId));
    }

    @Test
    @DisplayName("주문 취소 성공")
    void cancelOrder_Success() {
        // given
        Long userId = 1L;
        Long orderId = 1L;
        
        Order order = Order.create(userId, Recipient.create("수령인", "010-9876-5432"), 
                                 Address.create("12345", "서울시", "상세주소"));
        List<OrderItem> orderItems = List.of(
            OrderItem.create(orderId, 1L, null, Quantity.of(2), Money.of(10000))
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
        
        Order order = Order.create(userId, Recipient.create("수령인", "010-9876-5432"), 
                                 Address.create("12345", "서울시", "상세주소"));
        order.completePayment(); // 결제 완료 상태로 변경

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("취소할 수 없는 주문 상태입니다");
    }
}