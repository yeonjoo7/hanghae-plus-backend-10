package com.hanghae.ecommerce.infrastructure.config;

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
import com.hanghae.ecommerce.infrastructure.persistence.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @Bean
    public UserRepository userRepository() {
        return new InMemoryUserRepository();
    }

    @Bean
    public ProductRepository productRepository() {
        return new InMemoryProductRepository();
    }

    @Bean
    public ProductOptionRepository productOptionRepository() {
        return new InMemoryProductOptionRepository();
    }

    @Bean
    public StockRepository stockRepository() {
        return new InMemoryStockRepository();
    }

    @Bean
    public CouponRepository couponRepository() {
        return new InMemoryCouponRepository();
    }

    @Bean
    public UserCouponRepository userCouponRepository() {
        return new InMemoryUserCouponRepository();
    }

    @Bean
    public CartRepository cartRepository() {
        return new InMemoryCartRepository();
    }

    @Bean
    public CartItemRepository cartItemRepository() {
        return new InMemoryCartItemRepository();
    }

    @Bean
    public OrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    @Bean
    public OrderItemRepository orderItemRepository() {
        return new InMemoryOrderItemRepository();
    }

    @Bean
    public PaymentRepository paymentRepository() {
        return new InMemoryPaymentRepository();
    }

    @Bean
    public BalanceTransactionRepository balanceTransactionRepository() {
        return new InMemoryBalanceTransactionRepository();
    }
}