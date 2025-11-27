package com.hanghae.ecommerce.integration;

import com.hanghae.ecommerce.application.order.OrderService;
import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartItem;
import com.hanghae.ecommerce.domain.cart.repository.CartItemRepository;
import com.hanghae.ecommerce.domain.cart.repository.CartRepository;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.application.coupon.CouponService;
import com.hanghae.ecommerce.application.payment.PaymentService;
import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("주문 플로우 통합 테스트")
class OrderFlowIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private CouponService couponService;

        @Autowired
        private PaymentService paymentService;

        @Autowired
        private OrderService orderService;

        @Autowired
        private CartRepository cartRepository;

        @Autowired
        private CartItemRepository cartItemRepository;

        @BeforeEach
        void setUp() {
                // 스키마 생성
                executeSqlScript("/db/schema.sql");

                // 테스트 데이터 초기화
                jdbcTemplate.execute("DELETE FROM data_transmissions");
                jdbcTemplate.execute("DELETE FROM balance_transactions");
                jdbcTemplate.execute("DELETE FROM stock_movements");
                jdbcTemplate.execute("DELETE FROM order_items");
                jdbcTemplate.execute("DELETE FROM payments");
                jdbcTemplate.execute("DELETE FROM orders");
                jdbcTemplate.execute("DELETE FROM cart_items");
                jdbcTemplate.execute("DELETE FROM carts");
                jdbcTemplate.execute("DELETE FROM user_coupons");
                jdbcTemplate.execute("DELETE FROM product_options");
                jdbcTemplate.execute("DELETE FROM products");
                jdbcTemplate.execute("DELETE FROM coupons");
                jdbcTemplate.execute("DELETE FROM users");

                // 기본 데이터 생성 (Numeric IDs)
                jdbcTemplate
                                .update("INSERT INTO users(id, email, name, available_point) VALUES('1','user1@test.com', '테스트유저', 100000)");
                jdbcTemplate.update("INSERT INTO products(id, name, price, state) VALUES('1','노트북',50000,'NORMAL')");
                jdbcTemplate.update("INSERT INTO products(id, name, price, state) VALUES('2','마우스',10000,'NORMAL')");
                jdbcTemplate.update("INSERT INTO stocks(product_id, available_quantity) VALUES('1', 10)");
                jdbcTemplate.update("INSERT INTO stocks(product_id, available_quantity) VALUES('2', 20)");
                jdbcTemplate.update(
                                "INSERT INTO coupons(id, name, discount_type, discount_value, min_order_amount, total_quantity, issued_quantity, start_date, end_date) VALUES('1','10% 할인','PERCENTAGE',10,50000,100,0,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY))");
        }

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("전체 주문 플로우 - 쿠폰 발급부터 결제까지")
        void testFullOrderFlow() {
                Long userId = 1L;
                Long couponId = 1L;
                Long product1Id = 1L;
                Long product2Id = 2L;

                // 1. 쿠폰 발급
                UserCoupon userCoupon = couponService.issueCoupon(couponId, userId);
                assertThat(userCoupon).isNotNull();

                // 2. 장바구니 담기
                Cart cart = cartRepository.save(Cart.create(userId));
                CartItem item1 = cartItemRepository
                                .save(CartItem.createForProduct(cart.getId(), product1Id, Quantity.of(1)));
                CartItem item2 = cartItemRepository
                                .save(CartItem.createForProduct(cart.getId(), product2Id, Quantity.of(2)));

                List<Long> cartItemIds = List.of(item1.getId(), item2.getId());

                // 3. 주문 생성
                cart.applyCoupon(userCoupon.getId());
                cartRepository.save(cart);

                OrderService.OrderInfo orderInfo = orderService.createOrder(userId, cartItemIds, "홍길동", "010-1234-5678",
                                "12345", "서울시", "강남구");
                Order order = orderInfo.getOrder();

                // 50000*1 + 10000*2 = 70000
                // Discount 10% = 7000
                // Final = 63000
                assertThat(order.getAmount().getValue()).isEqualTo(70000);
                assertThat(order.getDiscountAmount().getValue()).isEqualTo(7000);
                assertThat(order.getTotalAmount().getValue()).isEqualTo(63000);

                // 4. 결제 처리
                Payment payment = paymentService.processPayment(String.valueOf(order.getId()), String.valueOf(userId),
                                PaymentMethod.POINT);
                assertThat(payment.getState().name()).isEqualTo("COMPLETED");

                // 5. 잔액 확인
                Integer availablePoint = jdbcTemplate.queryForObject("SELECT available_point FROM users WHERE id='1'",
                                Integer.class);
                assertThat(availablePoint).isEqualTo(37000);

                // 6. 재고 확인 (결제 시점에 재고 차감됨)
                // product1: 초기 10 -> 주문 생성 시 차감 없음 -> 결제 시 1개 차감 = 9
                // product2: 초기 20 -> 주문 생성 시 차감 없음 -> 결제 시 2개 차감 = 18
                Integer stock1 = jdbcTemplate.queryForObject(
                                "SELECT available_quantity FROM stocks WHERE product_id='1'", Integer.class);
                Integer stock2 = jdbcTemplate.queryForObject(
                                "SELECT available_quantity FROM stocks WHERE product_id='2'", Integer.class);
                assertThat(stock1).isEqualTo(9); // 10 - 1 (결제 시 차감)
                assertThat(stock2).isEqualTo(18); // 20 - 2 (결제 시 차감)

                // 7. 쿠폰 상태 확인
                String status = jdbcTemplate.queryForObject(
                                "SELECT state FROM user_coupons WHERE user_id='1' AND coupon_id='1'",
                                String.class);
                assertThat(status).isEqualTo("USED");

                // 8. 주문 상태 확인
                String orderStatus = jdbcTemplate.queryForObject(
                                "SELECT state FROM orders WHERE id=?",
                                String.class,
                                order.getId());
                assertThat(orderStatus).isEqualTo("COMPLETED");
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("재고 부족 시 롤백 확인")
        void testRollbackOnInsufficientStock() {
                Long userId = 1L;
                Long product1Id = 1L;

                // 재고를 1개로 제한
                jdbcTemplate.update("UPDATE stocks SET available_quantity=1 WHERE product_id='1'");

                // 2개 주문 시도 - 재고 부족으로 주문 생성 자체가 실패해야 함
                Cart cart = cartRepository.save(Cart.create(userId));
                CartItem item1 = cartItemRepository
                                .save(CartItem.createForProduct(cart.getId(), product1Id, Quantity.of(2)));
                List<Long> cartItemIds = List.of(item1.getId());

                // 주문 생성 시 재고 부족 예외 발생
                assertThatThrownBy(() -> orderService.createOrder(userId, cartItemIds, "홍길동", "010-1234-5678",
                                "12345", "서울시", "강남구"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("재고가 부족한 상품이 있습니다");

                // 재고 변경 없음 확인
                Integer stock = jdbcTemplate.queryForObject(
                                "SELECT available_quantity FROM stocks WHERE product_id='1'", Integer.class);
                assertThat(stock).isEqualTo(1);

                // 잔액 변경 없음 확인
                Integer availablePoint = jdbcTemplate.queryForObject("SELECT available_point FROM users WHERE id='1'",
                                Integer.class);
                assertThat(availablePoint).isEqualTo(100000);
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("잔액 부족 시 결제 실패")
        void testPaymentFailureOnInsufficientBalance() {
                Long userId = 1L;
                Long product1Id = 1L;

                // 잔액을 적게 설정
                jdbcTemplate.update("UPDATE users SET available_point=10000 WHERE id='1'");

                // 주문 생성
                Cart cart = cartRepository.save(Cart.create(userId));
                CartItem item1 = cartItemRepository
                                .save(CartItem.createForProduct(cart.getId(), product1Id, Quantity.of(1)));
                List<Long> cartItemIds = List.of(item1.getId());

                OrderService.OrderInfo orderInfo = orderService.createOrder(userId, cartItemIds, "홍길동", "010-1234-5678",
                                "12345", "서울시", "강남구");
                Order order = orderInfo.getOrder();

                // 결제 시도 시 잔액 부족 예외 발생
                assertThatThrownBy(() -> paymentService.processPayment(String.valueOf(order.getId()),
                                String.valueOf(userId),
                                PaymentMethod.POINT))
                                .hasMessageContaining("잔액");

                // 주문 상태 확인 (여전히 PENDING)
                String orderStatus = jdbcTemplate.queryForObject(
                                "SELECT state FROM orders WHERE id=?",
                                String.class,
                                order.getId());
                assertThat(orderStatus).isEqualTo("PENDING_PAYMENT");
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("쿠폰 중복 발급 방지")
        void testDuplicateCouponIssuePrevention() {
                Long userId = 1L;
                Long couponId = 1L;

                // 첫 번째 발급 성공
                UserCoupon firstCoupon = couponService.issueCoupon(couponId, userId);
                assertThat(firstCoupon).isNotNull();

                // 두 번째 발급 시도 시 예외 발생
                assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                                .hasMessageContaining("이미 발급");
        }

        private void executeSqlScript(String scriptPath) {
                try {
                        String sql = new String(getClass().getResourceAsStream(scriptPath).readAllBytes());
                        String[] statements = sql.split(";");
                        for (String statement : statements) {
                                if (!statement.trim().isEmpty()) {
                                        jdbcTemplate.execute(statement.trim());
                                }
                        }
                } catch (Exception e) {
                        // 스키마가 이미 존재하는 경우 무시
                }
        }
}