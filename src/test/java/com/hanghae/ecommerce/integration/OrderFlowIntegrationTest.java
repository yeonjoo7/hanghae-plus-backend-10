package com.hanghae.ecommerce.integration;

import com.hanghae.ecommerce.application.service.OrderService;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.payment.Payment;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.infrastructure.service.CouponServiceImpl;
import com.hanghae.ecommerce.infrastructure.service.PaymentServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("주문 플로우 통합 테스트")
class OrderFlowIntegrationTest {

    @Container
    private static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CouponServiceImpl couponService;

    @Autowired
    private PaymentServiceImpl paymentService;

    @Autowired
    private OrderService orderService;

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

        // 기본 데이터 생성
        jdbcTemplate.update("INSERT INTO users(id, email, name, balance) VALUES('user1','user1@test.com', '테스트유저', 100000)");
        jdbcTemplate.update("INSERT INTO products(id, name, price, stock, status) VALUES('P001','노트북',50000,10,'ACTIVE')");
        jdbcTemplate.update("INSERT INTO products(id, name, price, stock, status) VALUES('P002','마우스',10000,20,'ACTIVE')");
        jdbcTemplate.update("INSERT INTO coupons(id, name, discount_type, discount_value, min_order_amount, total_quantity, issued_quantity, start_date, end_date) VALUES('COUPON10','10% 할인','PERCENTAGE',10,50000,100,0,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY))");
    }

    @Test
    @Order(1)
    @DisplayName("전체 주문 플로우 - 쿠폰 발급부터 결제까지")
    void testFullOrderFlow() {
        // 1. 쿠폰 발급
        UserCoupon userCoupon = couponService.issueCoupon("COUPON10", "user1");
        assertThat(userCoupon).isNotNull();
        assertThat(userCoupon.getRemainingQuantity()).isEqualTo(99);

        // 2. 주문 생성
        List<Map<String, Object>> orderItems = List.of(
            Map.of("productId", "P001", "quantity", 1),
            Map.of("productId", "P002", "quantity", 2)
        );
        
        Order order = orderService.createOrder("user1", orderItems, "COUPON10", null);
        
        assertThat(order.getTotalAmount().getValue()).isEqualByComparingTo("70000");
        assertThat(order.getDiscountAmount().getValue()).isEqualByComparingTo("7000");
        assertThat(order.getFinalAmount().getValue()).isEqualByComparingTo("63000");

        // 3. 결제 처리
        Payment payment = paymentService.processPayment(order.getId(), "user1", PaymentMethod.BALANCE);
        assertThat(payment.getState().name()).isEqualTo("COMPLETED");

        // 4. 잔액 확인
        Double balance = jdbcTemplate.queryForObject("SELECT balance FROM users WHERE id='user1'", Double.class);
        assertThat(balance).isEqualTo(37000.0);

        // 5. 재고 확인
        Integer stock1 = jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id='P001'", Integer.class);
        Integer stock2 = jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id='P002'", Integer.class);
        assertThat(stock1).isEqualTo(9);
        assertThat(stock2).isEqualTo(18);

        // 6. 쿠폰 상태 확인
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM user_coupons WHERE user_id='user1' AND coupon_id='COUPON10'", 
            String.class
        );
        assertThat(status).isEqualTo("USED");
        
        // 7. 주문 상태 확인
        String orderStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id=?", 
            String.class, 
            order.getId()
        );
        assertThat(orderStatus).isEqualTo("PAID");
    }

    @Test
    @Order(2)
    @DisplayName("재고 부족 시 롤백 확인")
    void testRollbackOnInsufficientStock() {
        // 재고를 1개로 제한
        jdbcTemplate.update("UPDATE products SET stock=1 WHERE id='P001'");

        // 2개 주문 시도
        List<Map<String, Object>> orderItems = List.of(
            Map.of("productId", "P001", "quantity", 2)
        );
        
        Order order = orderService.createOrder("user1", orderItems, null, null);
        
        // 결제 시도 시 재고 부족 예외 발생
        assertThatThrownBy(() -> 
            paymentService.processPayment(order.getId(), "user1", PaymentMethod.BALANCE)
        )
        .hasMessageContaining("재고");

        // 재고 변경 없음 확인
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id='P001'", Integer.class);
        assertThat(stock).isEqualTo(1);
        
        // 잔액 변경 없음 확인
        Double balance = jdbcTemplate.queryForObject("SELECT balance FROM users WHERE id='user1'", Double.class);
        assertThat(balance).isEqualTo(100000.0);
    }

    @Test
    @Order(3)
    @DisplayName("잔액 부족 시 결제 실패")
    void testPaymentFailureOnInsufficientBalance() {
        // 잔액을 적게 설정
        jdbcTemplate.update("UPDATE users SET balance=10000 WHERE id='user1'");

        // 주문 생성
        List<Map<String, Object>> orderItems = List.of(
            Map.of("productId", "P001", "quantity", 1)
        );
        
        Order order = orderService.createOrder("user1", orderItems, null, null);

        // 결제 시도 시 잔액 부족 예외 발생
        assertThatThrownBy(() -> 
            paymentService.processPayment(order.getId(), "user1", PaymentMethod.BALANCE)
        )
        .hasMessageContaining("잔액");

        // 주문 상태 확인 (여전히 PENDING)
        String orderStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id=?", 
            String.class, 
            order.getId()
        );
        assertThat(orderStatus).isEqualTo("PENDING");
    }

    @Test
    @Order(4)
    @DisplayName("쿠폰 중복 발급 방지")
    void testDuplicateCouponIssuePrevention() {
        // 첫 번째 발급 성공
        UserCoupon firstCoupon = couponService.issueCoupon("COUPON10", "user1");
        assertThat(firstCoupon).isNotNull();

        // 두 번째 발급 시도 시 예외 발생
        assertThatThrownBy(() -> 
            couponService.issueCoupon("COUPON10", "user1")
        )
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