package com.hanghae.ecommerce.integration;

import com.hanghae.ecommerce.application.order.OrderService;
import com.hanghae.ecommerce.domain.cart.Cart;
import com.hanghae.ecommerce.domain.cart.CartItem;
import com.hanghae.ecommerce.domain.cart.repository.CartItemRepository;
import com.hanghae.ecommerce.domain.cart.repository.CartRepository;
import com.hanghae.ecommerce.domain.order.Order;
import com.hanghae.ecommerce.domain.payment.PaymentMethod;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.application.coupon.CouponService;
import com.hanghae.ecommerce.application.payment.PaymentService;
import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 통합 테스트
 * 
 * @deprecated 이 테스트는 인메모리 DB를 전제로 작성되어 MySQL 환경에서 실행되지 않습니다.
 *             대신 {@link OrderStockConcurrencyTest}와
 *             {@link BalanceDeductionConcurrencyTest}를 사용하세요.
 */
@Disabled("MySQL 기반 새로운 테스트로 대체됨 - OrderStockConcurrencyTest, BalanceDeductionConcurrencyTest 참조")
@DisplayName("동시성 통합 테스트 (DEPRECATED)")
class ConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CouponService couponService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() throws InterruptedException {
        // 스키마 생성
        executeSqlScript("/db/schema.sql");

        org.springframework.transaction.TransactionStatus status = transactionManager.getTransaction(
                new org.springframework.transaction.support.DefaultTransactionDefinition());
        try {
            // 데이터 초기화
            jdbcTemplate.execute("DELETE FROM user_coupons");
            jdbcTemplate.execute("DELETE FROM stocks");
            jdbcTemplate.execute("DELETE FROM coupons");
            jdbcTemplate.execute("DELETE FROM order_items");
            jdbcTemplate.execute("DELETE FROM payments");
            jdbcTemplate.execute("DELETE FROM orders");
            jdbcTemplate.execute("DELETE FROM cart_items");
            jdbcTemplate.execute("DELETE FROM carts");
            jdbcTemplate.execute("DELETE FROM products");
            jdbcTemplate.execute("DELETE FROM users");

            // 명시적으로 트랜잭션 커밋하여 데이터 정리를 DB에 반영
            transactionManager.commit(status);

            // 데이터가 모든 커넥션에 보이도록 잠시 대기
            Thread.sleep(100);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    @Test
    @DisplayName("선착순 쿠폰 동시 발급 - 10개 한정")
    void testConcurrentCouponIssuance() throws InterruptedException {
        Long couponId = 100L;

        // 10개 한정 쿠폰 생성
        jdbcTemplate.update(
                "INSERT INTO coupons(id, name, discount_type, discount_value, min_order_amount, total_quantity, issued_quantity, start_date, end_date) "
                        +
                        "VALUES(?,'선착순 쿠폰','PERCENTAGE',20,10000,10,0,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY))",
                couponId);

        // 20명 사용자 생성
        for (long i = 1; i <= 20; i++) {
            jdbcTemplate.update(
                    "INSERT INTO users(id,email,name,available_point) VALUES(?,?,?,?)",
                    i, "user" + i + "@test.com", "사용자" + i, 0);
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(20);
        List<Future<String>> results = new ArrayList<>();

        // 20명이 동시에 쿠폰 발급 시도
        for (long i = 1; i <= 20; i++) {
            final Long userId = i;
            results.add(executor.submit(() -> {
                try {
                    couponService.issueCoupon(couponId, userId);
                    return "SUCCESS";
                } catch (Exception e) {
                    e.printStackTrace(); // DEBUG
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
                "SELECT issued_quantity FROM coupons WHERE id=?",
                Integer.class,
                couponId);
        assertThat(issuedQuantity).isEqualTo(10);

        // 사용자 쿠폰 발급 수 확인
        Integer userCouponCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_coupons WHERE coupon_id=?",
                Integer.class,
                couponId);
        assertThat(userCouponCount).isEqualTo(10);
    }

    @Test
    @DisplayName("동시 주문 시 재고 정합성 확인")
    void testConcurrentOrderStockConsistency() throws InterruptedException {
        Long productId = 200L;

        // 재고 5개 상품 생성
        jdbcTemplate.update(
                "INSERT INTO products(id, name, price, state) VALUES(?,'한정상품',10000,'NORMAL')",
                productId);
        // 재고 테이블에 재고 추가
        jdbcTemplate.update(
                "INSERT INTO stocks(product_id, available_quantity) VALUES(?, 5)",
                productId);

        // 10명 사용자 생성 (충분한 잔액)
        for (long i = 1; i <= 10; i++) {
            jdbcTemplate.update(
                    "INSERT INTO users(id,email,name,available_point) VALUES(?,?,?,?)",
                    i, "user" + i + "@test.com", "사용자" + i, 50000);
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        List<Future<String>> results = new ArrayList<>();

        // 10명이 동시에 1개씩 주문
        for (long i = 1; i <= 10; i++) {
            final Long userId = i;
            results.add(executor.submit(() -> {
                try {
                    // 주문 생성
                    Cart cart = cartRepository.save(Cart.create(userId));
                    CartItem item = cartItemRepository
                            .save(CartItem.createForProduct(cart.getId(), productId, Quantity.of(1)));
                    List<Long> cartItemIds = List.of(item.getId());

                    OrderService.OrderInfo orderInfo = orderService.createOrder(userId, cartItemIds, "홍길동",
                            "010-1234-5678", "12345", "서울시", "강남구");
                    Order order = orderInfo.getOrder();

                    // 결제 처리
                    paymentService.processPayment(String.valueOf(order.getId()), String.valueOf(userId),
                            PaymentMethod.POINT);

                    return "SUCCESS";
                } catch (Exception e) {
                    e.printStackTrace(); // DEBUG
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
                "SELECT available_quantity FROM stocks WHERE product_id=?",
                Integer.class,
                productId);
        assertThat(stock).isEqualTo(0);

        // 성공한 주문 수 확인
        Integer paidOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE state='COMPLETED'",
                Integer.class);
        assertThat(paidOrders).isEqualTo(5);
    }

    @Test
    @DisplayName("동시 잔액 차감 시 정합성 확인")
    void testConcurrentBalanceDeduction() throws InterruptedException {
        Long userId = 1L;
        Long productId = 300L;

        // 사용자 생성 (잔액 100,000원)
        jdbcTemplate.update(
                "INSERT INTO users(id,email,name,available_point) VALUES(?,?,?,?)",
                userId, "user1@test.com", "사용자1", 100000);

        // 장바구니 생성 (1개만)
        Cart cart = cartRepository.save(Cart.create(userId));

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        List<Future<String>> results = new ArrayList<>();

        // 5개의 동시 주문 (각 10,000원)
        for (int i = 0; i < 5; i++) {
            final long currentProductId = productId + i;
            // 상품 생성
            jdbcTemplate.update(
                    "INSERT INTO products(id, name, price, state) VALUES(?, ?, 10000, 'NORMAL')",
                    currentProductId, "상품" + i);
            jdbcTemplate.update(
                    "INSERT INTO stocks(product_id, available_quantity) VALUES(?, 100)",
                    currentProductId);

            results.add(executor.submit(() -> {
                try {
                    // 장바구니 아이템 추가 (동시성 문제 방지를 위해 미리 추가하거나, 여기서 추가하되 서로 다른 상품임)
                    // 여기서 추가하면 CartItem insert가 동시에 일어남.
                    CartItem item = cartItemRepository
                            .save(CartItem.createForProduct(cart.getId(), currentProductId, Quantity.of(1)));
                    List<Long> cartItemIds = List.of(item.getId());

                    OrderService.OrderInfo orderInfo = orderService.createOrder(userId, cartItemIds, "홍길동",
                            "010-1234-5678", "12345", "서울시", "강남구");
                    Order order = orderInfo.getOrder();

                    // 결제 처리
                    paymentService.processPayment(String.valueOf(order.getId()), String.valueOf(userId),
                            PaymentMethod.POINT);

                    return "SUCCESS";
                } catch (Exception e) {
                    e.printStackTrace(); // DEBUG
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
        Integer balance = jdbcTemplate.queryForObject(
                "SELECT available_point FROM users WHERE id=?",
                Integer.class,
                userId);
        assertThat(balance).isEqualTo(50000);

        // 거래 내역 확인
        Integer transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM balance_transactions WHERE user_id=? AND type='USE'",
                Integer.class,
                userId);
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
            e.printStackTrace();
            // 스키마가 이미 존재하는 경우 무시
        }
    }
}