package com.hanghae.ecommerce.config;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.DiscountPolicy;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.domain.user.Point;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.UserState;
import com.hanghae.ecommerce.domain.user.UserType;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final CouponRepository couponRepository;

    public DataInitializer(UserRepository userRepository,
            ProductRepository productRepository,
            StockRepository stockRepository,
            CouponRepository couponRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.couponRepository = couponRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        initializeUsers();
        initializeProducts();
        initializeCoupons();
    }

    private void initializeUsers() {
        // 테스트용 사용자 생성
        User user = User.create(
                "test@example.com",
                "테스트 사용자",
                "010-1234-5678");
        userRepository.save(user);
    }

    private void initializeProducts() {
        // 상품 1: 노트북
        Product laptop = Product.create("MacBook Pro", "고성능 노트북", Money.of(1500000), Quantity.of(5));
        productRepository.save(laptop);

        // 간단한 초기화만 수행
        productRepository.save(laptop);
    }

    private void initializeCoupons() {
        // 초기화 생략 - 런타임에 추가
    }
}