package com.hanghae.ecommerce.config;

import com.hanghae.ecommerce.domain.cart.repository.CartItemRepository;
import com.hanghae.ecommerce.domain.cart.repository.CartRepository;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.hanghae.ecommerce.domain.order.repository.OrderItemRepository;
import com.hanghae.ecommerce.domain.order.repository.OrderRepository;
import com.hanghae.ecommerce.domain.payment.repository.BalanceTransactionRepository;
import com.hanghae.ecommerce.domain.payment.repository.PaymentRepository;
import com.hanghae.ecommerce.domain.product.repository.ProductOptionRepository;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.infrastructure.persistence.jpa.*;
import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

@Configuration
@Profile("test")
public class TestConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:test-schema.sql")
            .build();
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public UserRepository userRepository(JdbcTemplate jdbcTemplate) {
        return new JpaUserRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public ProductRepository productRepository(JdbcTemplate jdbcTemplate) {
        return new JpaProductRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public CouponRepository couponRepository(JdbcTemplate jdbcTemplate) {
        return new JpaCouponRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public UserCouponRepository userCouponRepository(JdbcTemplate jdbcTemplate) {
        return new JpaUserCouponRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public OrderRepository orderRepository(JdbcTemplate jdbcTemplate) {
        return new JpaOrderRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public PaymentRepository paymentRepository(JdbcTemplate jdbcTemplate) {
        return new JpaPaymentRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public BalanceTransactionRepository balanceTransactionRepository(JdbcTemplate jdbcTemplate) {
        return new JpaBalanceTransactionRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public CartRepository cartRepository(JdbcTemplate jdbcTemplate) {
        return new JpaCartRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public CartItemRepository cartItemRepository(JdbcTemplate jdbcTemplate) {
        return new JpaCartItemRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public OrderItemRepository orderItemRepository(JdbcTemplate jdbcTemplate) {
        return new JpaOrderItemRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public ProductOptionRepository productOptionRepository(JdbcTemplate jdbcTemplate) {
        return new JpaProductOptionRepository(jdbcTemplate);
    }

    @Bean
    @Primary
    public StockRepository stockRepository(JdbcTemplate jdbcTemplate) {
        return new JpaStockRepository(jdbcTemplate);
    }


    @Bean
    @Primary
    public LockManager lockManager() {
        return new TestLockManager();
    }

    /**
     * 테스트용 Lock Manager
     */
    public static class TestLockManager implements LockManager {
        @Override
        public boolean tryLock(String lockKey) {
            return true; // 테스트에서는 항상 락 획득 성공
        }
        
        @Override
        public boolean tryLock(String lockKey, long timeout, java.util.concurrent.TimeUnit timeUnit) {
            return true; // 테스트에서는 항상 락 획득 성공
        }
        
        @Override
        public void unlock(String lockKey) {
            // 테스트용 - 아무것도 안함
        }
        
        @Override
        public <T> T executeWithLock(String lockKey, com.hanghae.ecommerce.infrastructure.lock.LockManager.LockTask<T> task) {
            try {
                return task.execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        @Override
        public <T> T executeWithLock(String lockKey, long timeout, java.util.concurrent.TimeUnit timeUnit, com.hanghae.ecommerce.infrastructure.lock.LockManager.LockTask<T> task) {
            try {
                return task.execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        @Override
        public int getActiveLockCount() {
            return 0; // 테스트에서는 항상 0
        }
        
        @Override
        public void clearAllLocks() {
            // 테스트용 - 아무것도 안함
        }
    }
}