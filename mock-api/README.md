# E-Commerce Mock API Server

이커머스 시스템 API 명세서를 기반으로 한 Mock API 서버입니다.
메모리 기반 데이터를 사용하여 간단한 CRUD 테스트 및 응답 형식 검증을 수행할 수 있습니다.

## 기술 스택

- Java 17
- Spring Boot 3.2.0
- Maven
- Lombok

## 프로젝트 구조

```
mock-api/
├── src/main/java/com/hanghae/ecommerce/
│   ├── MockApiApplication.java          # 메인 애플리케이션
│   ├── common/
│   │   └── ApiResponse.java             # 공통 응답 포맷
│   ├── product/
│   │   ├── controller/
│   │   │   └── ProductController.java   # 상품 API
│   │   └── dto/
│   │       ├── ProductResponse.java
│   │       └── ProductListResponse.java
│   ├── cart/
│   │   ├── controller/
│   │   │   └── CartController.java      # 장바구니 API
│   │   └── dto/
│   │       ├── CartRequest.java
│   │       └── CartResponse.java
│   ├── order/
│   │   ├── controller/
│   │   │   └── OrderController.java     # 주문 API
│   │   └── dto/
│   │       ├── OrderRequest.java
│   │       └── OrderResponse.java
│   ├── payment/
│   │   ├── controller/
│   │   │   └── PaymentController.java   # 결제 API
│   │   └── dto/
│   │       └── PaymentRequest.java
│   ├── coupon/
│   │   └── controller/
│   │       └── CouponController.java    # 쿠폰 API
│   └── balance/
│       ├── controller/
│       │   └── BalanceController.java   # 잔액 API
│       └── dto/
│           └── ChargeRequest.java
└── src/main/resources/
    └── application.yml                   # 설정 파일
```

## 실행 방법

### 1. 프로젝트 빌드

```bash
cd mock-api
mvn clean package
```

### 2. 서버 실행

```bash
mvn spring-boot:run
```

서버는 기본적으로 `http://localhost:8080`에서 실행됩니다.

### 3. 서버 실행 확인

```bash
curl http://localhost:8080/v1/products/1
```

## 구현된 API 엔드포인트

### 1. 상품 API (`/v1/products`)

- `GET /v1/products/{productId}` - 상품 상세 조회
- `GET /v1/products?ids=1,2,3` - 여러 상품 조회
- `GET /v1/products/popular` - 인기 상품 조회

### 2. 장바구니 API (`/v1/carts`)

- `GET /v1/carts` - 장바구니 조회
- `POST /v1/carts/items` - 장바구니에 상품 추가
- `PATCH /v1/carts/items/{cartItemId}` - 장바구니 상품 수량 변경
- `DELETE /v1/carts/items/{cartItemId}` - 장바구니 상품 삭제

### 3. 주문 API (`/v1/orders`)

- `POST /v1/orders` - 주문 생성
- `GET /v1/orders/{orderId}` - 주문 조회
- `GET /v1/orders` - 주문 목록 조회

### 4. 결제 API (`/v1/orders/{orderId}/payment`)

- `POST /v1/orders/{orderId}/payment` - 주문 결제

### 5. 쿠폰 API (`/v1/coupons`)

- `POST /v1/coupons/{couponId}/issue` - 쿠폰 발급
- `GET /v1/coupons/my` - 보유 쿠폰 조회
- `GET /v1/coupons/usage-history` - 쿠폰 사용 이력 조회

### 6. 잔액 API (`/v1/balance`)

- `POST /v1/balance/charge` - 잔액 충전
- `GET /v1/balance` - 잔액 조회
- `GET /v1/balance/history` - 잔액 사용 이력 조회

## API 테스트 예제

### 상품 조회

```bash
# 상품 상세 조회
curl -X GET http://localhost:8080/v1/products/1

# 여러 상품 조회
curl -X GET "http://localhost:8080/v1/products?ids=1,2,3"

# 인기 상품 조회
curl -X GET http://localhost:8080/v1/products/popular
```

### 장바구니 관리

```bash
# 장바구니 조회
curl -X GET http://localhost:8080/v1/carts

# 장바구니에 상품 추가
curl -X POST http://localhost:8080/v1/carts/items \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "quantity": 2
  }'

# 장바구니 상품 수량 변경
curl -X PATCH http://localhost:8080/v1/carts/items/1 \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 3
  }'

# 장바구니 상품 삭제
curl -X DELETE http://localhost:8080/v1/carts/items/1
```

### 주문 생성 및 결제

```bash
# 주문 생성
curl -X POST http://localhost:8080/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "cartItemIds": [1, 2],
    "shippingAddress": {
      "recipientName": "홍길동",
      "phone": "010-1234-5678",
      "zipCode": "12345",
      "address": "서울시 강남구 테헤란로 123",
      "detailAddress": "456호"
    }
  }'

# 주문 결제
curl -X POST http://localhost:8080/v1/orders/1001/payment \
  -H "Content-Type: application/json" \
  -d '{
    "paymentMethod": "BALANCE",
    "couponIds": [1, 2]
  }'
```

### 쿠폰 발급 및 조회

```bash
# 쿠폰 발급
curl -X POST http://localhost:8080/v1/coupons/1/issue

# 보유 쿠폰 조회
curl -X GET http://localhost:8080/v1/coupons/my

# 사용 가능한 쿠폰만 조회
curl -X GET "http://localhost:8080/v1/coupons/my?status=AVAILABLE"
```

### 잔액 충전 및 조회

```bash
# 잔액 충전
curl -X POST http://localhost:8080/v1/balance/charge \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100000
  }'

# 잔액 조회
curl -X GET http://localhost:8080/v1/balance

# 잔액 사용 이력 조회
curl -X GET http://localhost:8080/v1/balance/history
```

## 응답 형식

### 성공 응답

```json
{
  "success": true,
  "data": {
    // 응답 데이터
  },
  "message": "요청이 성공적으로 처리되었습니다",
  "error": null
}
```

### 에러 응답

```json
{
  "success": false,
  "data": null,
  "message": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지",
    "details": {}
  }
}
```

## 초기 Mock 데이터

서버 시작 시 다음 상품이 자동으로 생성됩니다:

1. 노트북 (1,500,000원, 재고 50개)
2. 마우스 (30,000원, 재고 100개)
3. 키보드 (150,000원, 재고 75개)
4. 모니터 (400,000원, 재고 30개)
5. 무선 이어폰 (150,000원, 재고 100개)

## 주의사항

- 이 서버는 **메모리 기반**으로 동작하므로 서버 재시작 시 모든 데이터가 초기화됩니다.
- 인증/인가 기능은 구현되어 있지 않습니다.
- 실제 비즈니스 로직은 구현되어 있지 않으며, **응답 형식 테스트 용도**로만 사용하세요.
- 데이터 유효성 검증은 최소한으로만 구현되어 있습니다.

## 관련 문서

- [API 명세서](../docs/api/api-specification.md)
- [데이터 모델](../docs/api/data-model.md)
- [시퀀스 다이어그램](../docs/api/sequence-diagram.puml)

## 라이선스

MIT License
