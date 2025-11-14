package com.hanghae.ecommerce.integration;

import com.hanghae.ecommerce.application.service.OrderService;
import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("동시성 통합 테스트")
class ConcurrencyIntegrationTest {

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
    private OrderService orderService;
    
    @Autowired
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        // 스키마 생성
        executeSqlScript("/db/schema.sql");
        
        // 데이터 초기화
        jdbcTemplate.execute("DELETE FROM user_coupons");
        jdbcTemplate.execute("DELETE FROM coupons");
        jdbcTemplate.execute("DELETE FROM order_items");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM products");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Test
    @DisplayName("선착순 쿠폰 동시 발급 - 10개 한정")
    void testConcurrentCouponIssuance() throws InterruptedException {
        // 10개 한정 쿠폰 생성
        jdbcTemplate.update(
            "INSERT INTO coupons(id, name, discount_type, discount_value, min_order_amount, total_quantity, issued_quantity, start_date, end_date) " +
            "VALUES('LIMITED','선착순 쿠폰','PERCENTAGE',20,10000,10,0,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY))"
        );
        
        // 20명 사용자 생성
        for (int i = 1; i <= 20; i++) {
            jdbcTemplate.update(
                "INSERT INTO users(id,email,name,balance) VALUES(?,?,?,?)", 
                "user" + i, "user" + i + "@test.com", "사용자" + i, 0
            );
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(20);
        List<Future<String>> results = new ArrayList<>();

        // 20명이 동시에 쿠폰 발급 시도
        for (int i = 1; i <= 20; i++) {
            final int userIndex = i;
            results.add(executor.submit(() -> {
                try {
                    couponService.issueCoupon("LIMITED", "user" + userIndex);
                    return "SUCCESS";
                } catch (Exception e) {
                    return "FAIL";
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await();
        executor.shutdown();

        // 결과 확인
        long successCount = results.stream()
            .filter(f -> {
                try {
                    return f.get().equals("SUCCESS");
                } catch (Exception e) {
                    return false;
                }
            })
            .count();

        assertThat(successCount).isEqualTo(10);

        // 데이터베이스 확인
        Integer issuedQuantity = jdbcTemplate.queryForObject(
            "SELECT issued_quantity FROM coupons WHERE id='LIMITED'", 
            Integer.class
        );
        assertThat(issuedQuantity).isEqualTo(10);

        // 사용자 쿠폰 발급 수 확인
        Integer userCouponCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_coupons WHERE coupon_id='LIMITED'", 
            Integer.class
        );
        assertThat(userCouponCount).isEqualTo(10);
    }

    @Test
    @DisplayName("동시 주문 시 재고 정합성 확인")
    void testConcurrentOrderStockConsistency() throws InterruptedException {
        // 재고 5개 상품 생성
        jdbcTemplate.update(
            "INSERT INTO products(id, name, price, stock, status) VALUES('LIMITED_PROD','한정상품',10000,5,'ACTIVE')"
        );

        // 10명 사용자 생성 (충분한 잔액)
        for (int i = 1; i <= 10; i++) {
            jdbcTemplate.update(
                "INSERT INTO users(id,email,name,balance) VALUES(?,?,?,?)", 
                "user" + i, "user" + i + "@test.com", "사용자" + i, 50000
            );
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        List<Future<String>> results = new ArrayList<>();

        // 10명이 동시에 1개씩 주문
        for (int i = 1; i <= 10; i++) {
            final int userIndex = i;
            results.add(executor.submit(() -> {
                try {
                    // 주문 생성
                    List<Map<String, Object>> orderItems = List.of(
                        Map.of("productId", "LIMITED_PROD", "quantity", 1)
                    );
                    Order order = orderService.createOrder("user" + userIndex, orderItems, null, null);
                    
                    // 결제 처리
                    paymentService.processPayment(order.getId(), "user" + userIndex, PaymentMethod.BALANCE);
                    
                    return "SUCCESS";
                } catch (Exception e) {
                    return "FAIL";
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await();
        executor.shutdown();

        // 결과 확인 - 5명만 성공해야 함
        long successCount = results.stream()
            .filter(f -> {
                try {
                    return f.get().equals("SUCCESS");
                } catch (Exception e) {
                    return false;
                }
            })
            .count();

        assertThat(successCount).isEqualTo(5);

        // 재고 확인 - 0이어야 함
        Integer stock = jdbcTemplate.queryForObject(
            "SELECT stock FROM products WHERE id='LIMITED_PROD'", 
            Integer.class
        );
        assertThat(stock).isEqualTo(0);

        // 성공한 주문 수 확인
        Integer paidOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE status='PAID'", 
            Integer.class
        );
        assertThat(paidOrders).isEqualTo(5);
    }

    @Test
    @DisplayName("동시 잔액 차감 시 정합성 확인")
    void testConcurrentBalanceDeduction() throws InterruptedException {
        // 사용자 생성 (잔액 100,000원)
        jdbcTemplate.update(
            "INSERT INTO users(id,email,name,balance) VALUES('user1','user1@test.com','사용자1',100000)"
        );

        // 상품 생성 (충분한 재고)
        jdbcTemplate.update(
            "INSERT INTO products(id, name, price, stock, status) VALUES('P001','상품1',10000,100,'ACTIVE')"
        );

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        List<Future<String>> results = new ArrayList<>();

        // 5개의 동시 주문 (각 10,000원)
        for (int i = 0; i < 5; i++) {
            results.add(executor.submit(() -> {
                try {
                    // 주문 생성
                    List<Map<String, Object>> orderItems = List.of(
                        Map.of("productId", "P001", "quantity", 1)
                    );
                    Order order = orderService.createOrder("user1", orderItems, null, null);
                    
                    // 결제 처리
                    paymentService.processPayment(order.getId(), "user1", PaymentMethod.BALANCE);
                    
                    return "SUCCESS";
                } catch (Exception e) {
                    return "FAIL";
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await();
        executor.shutdown();

        // 모든 요청이 성공해야 함
        long successCount = results.stream()
            .filter(f -> {
                try {
                    return f.get().equals("SUCCESS");
                } catch (Exception e) {
                    return false;
                }
            })
            .count();

        assertThat(successCount).isEqualTo(5);

        // 잔액 확인 - 50,000원이어야 함
        Double balance = jdbcTemplate.queryForObject(
            "SELECT balance FROM users WHERE id='user1'", 
            Double.class
        );
        assertThat(balance).isEqualTo(50000.0);

        // 거래 내역 확인
        Integer transactionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM balance_transactions WHERE user_id='user1' AND type='USE'", 
            Integer.class
        );
        assertThat(transactionCount).isEqualTo(5);
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