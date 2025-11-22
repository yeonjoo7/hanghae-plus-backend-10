package com.hanghae.ecommerce.concurrency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.DiscountPolicy;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.product.Money;
import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.Quantity;
import com.hanghae.ecommerce.domain.product.Stock;
import com.hanghae.ecommerce.domain.product.repository.ProductRepository;
import com.hanghae.ecommerce.domain.product.repository.StockRepository;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import com.hanghae.ecommerce.support.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 기반 동시성 테스트를 위한 베이스 클래스
 * MockMvc를 사용하여 실제 HTTP API 엔드포인트를 테스트합니다.
 */
@AutoConfigureMockMvc
public abstract class BaseConcurrencyTest extends BaseIntegrationTest {

  @Autowired
  protected MockMvc mockMvc;

  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  protected CouponRepository couponRepository;

  @Autowired
  protected com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository userCouponRepository;

  @Autowired
  protected UserRepository userRepository;

  @Autowired
  protected ProductRepository productRepository;

  @Autowired
  protected StockRepository stockRepository;

  /**
   * 테스트용 쿠폰 생성
   */
  protected Coupon createCoupon(String name, int totalQuantity, int discountRate) {
    Coupon coupon = Coupon.create(
        name,
        DiscountPolicy.rate(discountRate),
        Quantity.of(totalQuantity),
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now().plusDays(7));
    return couponRepository.save(coupon);
  }

  /**
   * 테스트용 사용자 생성
   */
  protected User createUser(String email, String name, int availablePoint) {
    User user = User.create(email, name, "010-1234-5678");
    if (availablePoint > 0) {
      user.chargePoint(com.hanghae.ecommerce.domain.user.Point.of(availablePoint));
    }
    return userRepository.save(user);
  }

  /**
   * 여러 사용자 생성
   */
  protected List<User> createUsers(int count) {
    List<User> users = new ArrayList<>();
    long timestamp = System.currentTimeMillis();
    for (int i = 0; i < count; i++) {
      User user = createUser(
          "user" + i + "_" + timestamp + "@test.com",
          "테스트유저" + i,
          100000 // 기본 10만원
      );
      users.add(user);
    }
    return users;
  }

  /**
   * 테스트용 상품 생성 (재고 포함)
   */
  protected Product createProduct(String name, int price, int stockQuantity) {
    Product product = Product.create(
        name,
        "테스트 상품",
        Money.of(price),
        Quantity.of(10) // 제한 수량
    );
    product = productRepository.save(product);

    // 재고 생성
    Stock stock = Stock.createForProduct(product.getId(), Quantity.of(stockQuantity), "테스트 재고");
    stockRepository.save(stock);

    return product;
  }

  /**
   * 여러 상품 생성
   */
  protected List<Product> createProducts(int count, int priceEach, int stockEach) {
    List<Product> products = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Product product = createProduct(
          "상품" + i,
          priceEach,
          stockEach);
      products.add(product);
    }
    return products;
  }

  /**
   * JWT 토큰 생성 (테스트용 간단한 구현)
   * 실제 프로덕션에서는 JWT 라이브러리를 사용해야 하지만,
   * 테스트 환경에서는 userId를 포함한 간단한 토큰을 생성합니다.
   */
  protected String generateToken(User user) {
    // 간단한 JWT 형식의 토큰 생성 (header.payload.signature)
    // 실제로는 서명이 필요하지만 테스트용으로 단순화
    try {
      String header = java.util.Base64.getEncoder().encodeToString(
          "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());

      String payload = java.util.Base64.getEncoder().encodeToString(
          String.format("{\"userId\":%d,\"email\":\"%s\"}",
              user.getId(), user.getEmail()).getBytes());

      String signature = java.util.Base64.getEncoder().encodeToString(
          "test-signature".getBytes());

      return header + "." + payload + "." + signature;
    } catch (Exception e) {
      // 토큰 생성 실패 시 간단한 형식으로 폴백
      return "Bearer-User-" + user.getId();
    }
  }

  /**
   * JSON 응답에서 특정 필드 값 추출
   */
  protected Long extractLongField(String jsonResponse, String fieldName) throws Exception {
    JsonNode root = objectMapper.readTree(jsonResponse);
    JsonNode dataNode = root.path("data");
    return dataNode.path(fieldName).asLong();
  }

  /**
   * JSON 응답에서 cartItemId 추출
   */
  protected Long extractCartItemId(String jsonResponse) throws Exception {
    return extractLongField(jsonResponse, "cartItemId");
  }

  /**
   * JSON 응답에서 orderId 추출
   */
  protected Long extractOrderId(String jsonResponse) throws Exception {
    return extractLongField(jsonResponse, "orderId");
  }

  /**
   * 주문 생성 요청 JSON 생성
   */
  protected String createOrderRequest(Long cartItemId) throws Exception {
    return objectMapper.writeValueAsString(new OrderRequest(
        List.of(cartItemId),
        new ShippingAddress(
            "홍길동",
            "010-1234-5678",
            "12345",
            "서울시 강남구",
            "테헤란로 123")));
  }

  /**
   * 주문 요청 DTO
   */
  protected static class OrderRequest {
    public List<Long> cartItemIds;
    public ShippingAddress shippingAddress;

    public OrderRequest(List<Long> cartItemIds, ShippingAddress shippingAddress) {
      this.cartItemIds = cartItemIds;
      this.shippingAddress = shippingAddress;
    }
  }

  /**
   * 배송지 정보 DTO
   */
  protected static class ShippingAddress {
    public String recipientName;
    public String phone;
    public String zipCode;
    public String address;
    public String detailAddress;

    public ShippingAddress(String recipientName, String phone, String zipCode,
        String address, String detailAddress) {
      this.recipientName = recipientName;
      this.phone = phone;
      this.zipCode = zipCode;
      this.address = address;
      this.detailAddress = detailAddress;
    }
  }
}
