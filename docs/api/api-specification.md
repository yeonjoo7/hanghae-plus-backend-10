# 이커머스 시스템 RESTful API 명세서

## 목차
1. [개요](#개요)
2. [공통 사항](#공통-사항)
3. [상품 API](#1-상품-api)
4. [장바구니 API](#2-장바구니-api)
5. [주문 API](#3-주문-api)
6. [결제 API](#4-결제-api)
7. [쿠폰 API](#5-쿠폰-api)
8. [잔액 API](#6-잔액-api)
9. [관리자 API](#7-관리자-api)
10. [에러 코드](#에러-코드)

---

## 개요

본 문서는 이커머스 시스템의 RESTful API 명세를 정의합니다.

### Base URL
```
https://api.ecommerce.com/v1
```

### 버전 정보
- API Version: v1
- 문서 버전: 1.0

---

## 공통 사항

### 인증
모든 API는 JWT 토큰 기반 인증을 사용합니다.

```http
Authorization: Bearer {access_token}
```

### 공통 헤더
```http
Content-Type: application/json
Accept: application/json
```

### 공통 응답 형식

#### 성공 응답
```json
{
  "success": true,
  "data": {
    // 응답 데이터
  },
  "message": "요청이 성공적으로 처리되었습니다"
}
```

#### 에러 응답
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지",
    "details": {}
  }
}
```

### HTTP 상태 코드
- `200 OK`: 요청 성공
- `201 Created`: 리소스 생성 성공
- `204 No Content`: 성공했으나 응답 본문 없음
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 실패
- `403 Forbidden`: 권한 없음
- `404 Not Found`: 리소스 없음
- `409 Conflict`: 리소스 충돌
- `422 Unprocessable Entity`: 유효성 검증 실패
- `500 Internal Server Error`: 서버 오류

---

## 1. 상품 API

### 1.1 상품 상세 조회

단일 상품의 상세 정보를 조회합니다.

```http
GET /products/{productId}
```

#### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| productId | Long | Yes | 상품 ID |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "productId": 1,
    "name": "노트북",
    "description": "고성능 노트북",
    "price": 1500000,
    "stock": 50,
    "maxQuantityPerCart": 5,
    "status": "AVAILABLE",
    "createdAt": "2025-10-01T10:00:00Z",
    "updatedAt": "2025-10-31T10:00:00Z"
  }
}
```

#### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| productId | Long | 상품 ID |
| name | String | 상품명 |
| description | String | 상품 설명 |
| price | Integer | 가격 (원) |
| stock | Integer | 재고 수량 |
| maxQuantityPerCart | Integer | 장바구니 제한 수량 (null 가능) |
| status | String | 상품 상태 (AVAILABLE, OUT_OF_STOCK) |
| createdAt | DateTime | 생성일시 |
| updatedAt | DateTime | 수정일시 |

#### Error Responses
- `404 Not Found`: 상품을 찾을 수 없음
```json
{
  "success": false,
  "error": {
    "code": "PRODUCT_NOT_FOUND",
    "message": "상품을 찾을 수 없습니다"
  }
}
```

---

### 1.2 여러 상품 조회

여러 상품을 한 번에 조회합니다.

```http
GET /products?ids={productIds}
```

#### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| ids | String | Yes | 상품 ID 목록 (쉼표로 구분) |

#### Example Request
```http
GET /products?ids=1,2,3,4,5
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "products": [
      {
        "productId": 1,
        "name": "노트북",
        "price": 1500000,
        "stock": 50,
        "status": "AVAILABLE"
      },
      {
        "productId": 2,
        "name": "마우스",
        "price": 30000,
        "stock": 0,
        "status": "OUT_OF_STOCK"
      }
    ],
    "totalCount": 2
  }
}
```

---

### 1.3 인기 상품 조회

최근 3일간 판매량 기준 상위 5개 상품을 조회합니다.

```http
GET /products/popular
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "products": [
      {
        "rank": 1,
        "productId": 10,
        "name": "무선 이어폰",
        "price": 150000,
        "stock": 100,
        "salesCount": 250,
        "salesPeriod": {
          "startDate": "2025-10-28",
          "endDate": "2025-10-31"
        }
      }
    ],
    "updatedAt": "2025-10-31T00:00:00Z"
  }
}
```

---

## 2. 장바구니 API

### 2.1 장바구니 조회

현재 사용자의 장바구니를 조회합니다.

```http
GET /carts
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "cartId": 123,
    "userId": 1,
    "items": [
      {
        "cartItemId": 1,
        "productId": 10,
        "productName": "노트북",
        "price": 1500000,
        "quantity": 2,
        "subtotal": 3000000,
        "stock": 50,
        "maxQuantityPerCart": 5
      }
    ],
    "totalAmount": 3000000,
    "itemCount": 1,
    "updatedAt": "2025-10-31T10:00:00Z"
  }
}
```

---

### 2.2 장바구니에 상품 추가

장바구니에 상품을 추가합니다.

```http
POST /carts/items
```

#### Request Body
```json
{
  "productId": 10,
  "quantity": 2
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| productId | Long | Yes | 상품 ID |
| quantity | Integer | Yes | 수량 (1 이상) |

#### Response (201 Created)
```json
{
  "success": true,
  "data": {
    "cartItemId": 1,
    "productId": 10,
    "productName": "노트북",
    "quantity": 2,
    "price": 1500000,
    "subtotal": 3000000
  },
  "message": "장바구니에 상품이 추가되었습니다"
}
```

#### Error Responses
- `404 Not Found`: 상품을 찾을 수 없음
- `400 Bad Request`: 재고 부족
```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "재고가 부족합니다",
    "details": {
      "requestedQuantity": 10,
      "availableStock": 5
    }
  }
}
```

- `400 Bad Request`: 제한 수량 초과
```json
{
  "success": false,
  "error": {
    "code": "EXCEED_MAX_QUANTITY",
    "message": "장바구니 제한 수량을 초과했습니다",
    "details": {
      "maxQuantityPerCart": 5,
      "requestedQuantity": 10
    }
  }
}
```

---

### 2.3 장바구니 상품 수량 변경

장바구니 상품의 수량을 변경합니다.

```http
PATCH /carts/items/{cartItemId}
```

#### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| cartItemId | Long | Yes | 장바구니 아이템 ID |

#### Request Body
```json
{
  "quantity": 3
}
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "cartItemId": 1,
    "quantity": 3,
    "subtotal": 4500000
  }
}
```

#### Error Responses
- `404 Not Found`: 장바구니 아이템을 찾을 수 없음
- `400 Bad Request`: 재고 부족 또는 제한 수량 초과

---

### 2.4 장바구니 상품 삭제

장바구니에서 상품을 삭제합니다.

```http
DELETE /carts/items/{cartItemId}
```

#### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| cartItemId | Long | Yes | 장바구니 아이템 ID |

#### Response (204 No Content)

#### Error Responses
- `404 Not Found`: 장바구니 아이템을 찾을 수 없음

---

## 3. 주문 API

### 3.1 주문 생성

장바구니의 상품들로 주문을 생성합니다.

```http
POST /orders
```

#### Request Body
```json
{
  "cartItemIds": [1, 2, 3],
  "shippingAddress": {
    "recipientName": "홍길동",
    "phone": "010-1234-5678",
    "zipCode": "12345",
    "address": "서울시 강남구 테헤란로 123",
    "detailAddress": "456호"
  }
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| cartItemIds | Long[] | Yes | 주문할 장바구니 아이템 ID 목록 |
| shippingAddress | Object | Yes | 배송지 정보 |

#### Response (201 Created)
```json
{
  "success": true,
  "data": {
    "orderId": 1001,
    "orderNumber": "ORD-20251031-1001",
    "status": "PENDING_PAYMENT",
    "orderItems": [
      {
        "orderItemId": 1,
        "productId": 10,
        "productName": "노트북",
        "price": 1500000,
        "quantity": 2,
        "subtotal": 3000000
      }
    ],
    "totalAmount": 3000000,
    "shippingAddress": {
      "recipientName": "홍길동",
      "phone": "010-1234-5678",
      "zipCode": "12345",
      "address": "서울시 강남구 테헤란로 123",
      "detailAddress": "456호"
    },
    "createdAt": "2025-10-31T10:00:00Z"
  }
}
```

#### Error Responses
- `400 Bad Request`: 재고 부족
```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "재고가 부족한 상품이 있습니다",
    "details": {
      "outOfStockProducts": [
        {
          "productId": 10,
          "productName": "노트북",
          "requestedQuantity": 5,
          "availableStock": 3
        }
      ]
    }
  }
}
```

---

### 3.2 주문 조회

특정 주문의 상세 정보를 조회합니다.

```http
GET /orders/{orderId}
```

#### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| orderId | Long | Yes | 주문 ID |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "orderId": 1001,
    "orderNumber": "ORD-20251031-1001",
    "userId": 1,
    "status": "COMPLETED",
    "orderItems": [
      {
        "orderItemId": 1,
        "productId": 10,
        "productName": "노트북",
        "price": 1500000,
        "quantity": 2,
        "subtotal": 3000000
      }
    ],
    "payment": {
      "paymentId": 1,
      "method": "BALANCE",
      "originalAmount": 3000000,
      "discountAmount": 100000,
      "finalAmount": 2900000,
      "status": "COMPLETED",
      "paidAt": "2025-10-31T10:05:00Z"
    },
    "appliedCoupons": [
      {
        "couponId": 10,
        "couponName": "10% 할인 쿠폰",
        "discountAmount": 100000
      }
    ],
    "shippingAddress": {
      "recipientName": "홍길동",
      "phone": "010-1234-5678",
      "zipCode": "12345",
      "address": "서울시 강남구 테헤란로 123",
      "detailAddress": "456호"
    },
    "createdAt": "2025-10-31T10:00:00Z",
    "updatedAt": "2025-10-31T10:05:00Z"
  }
}
```

#### Error Responses
- `404 Not Found`: 주문을 찾을 수 없음
- `403 Forbidden`: 본인의 주문이 아님

---

### 3.3 주문 목록 조회

사용자의 주문 목록을 조회합니다.

```http
GET /orders
```

#### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | Integer | No | 페이지 번호 (기본값: 1) |
| size | Integer | No | 페이지 크기 (기본값: 20) |
| status | String | No | 주문 상태 필터 |
| startDate | Date | No | 조회 시작일 (YYYY-MM-DD) |
| endDate | Date | No | 조회 종료일 (YYYY-MM-DD) |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "orders": [
      {
        "orderId": 1001,
        "orderNumber": "ORD-20251031-1001",
        "status": "COMPLETED",
        "totalAmount": 3000000,
        "discountAmount": 100000,
        "finalAmount": 2900000,
        "itemCount": 2,
        "createdAt": "2025-10-31T10:00:00Z"
      }
    ],
    "pagination": {
      "currentPage": 1,
      "totalPages": 5,
      "totalItems": 100,
      "itemsPerPage": 20
    }
  }
}
```

---

## 4. 결제 API

### 4.1 주문 결제

주문에 대한 결제를 처리합니다.

```http
POST /orders/{orderId}/payment
```

#### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| orderId | Long | Yes | 주문 ID |

#### Request Body
```json
{
  "paymentMethod": "BALANCE",
  "couponIds": [10, 20]
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| paymentMethod | String | Yes | 결제 수단 (BALANCE) |
| couponIds | Long[] | No | 사용할 쿠폰 ID 목록 |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "paymentId": 1,
    "orderId": 1001,
    "orderNumber": "ORD-20251031-1001",
    "paymentMethod": "BALANCE",
    "originalAmount": 3000000,
    "discountAmount": 300000,
    "finalAmount": 2700000,
    "appliedCoupons": [
      {
        "couponId": 10,
        "couponName": "장바구니 10% 할인",
        "couponType": "CART",
        "discountAmount": 200000
      },
      {
        "couponId": 20,
        "couponName": "특정 상품 할인",
        "couponType": "CART_ITEM",
        "discountAmount": 100000,
        "appliedProductId": 10
      }
    ],
    "balance": {
      "before": 5000000,
      "after": 2300000,
      "used": 2700000
    },
    "status": "COMPLETED",
    "paidAt": "2025-10-31T10:05:00Z"
  },
  "message": "결제가 완료되었습니다"
}
```

#### Error Responses
- `404 Not Found`: 주문을 찾을 수 없음
- `400 Bad Request`: 잔액 부족
```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "잔액이 부족합니다",
    "details": {
      "requiredAmount": 2700000,
      "currentBalance": 2000000,
      "shortfall": 700000
    }
  }
}
```

- `400 Bad Request`: 쿠폰 사용 개수 초과
```json
{
  "success": false,
  "error": {
    "code": "EXCEED_COUPON_LIMIT",
    "message": "쿠폰 사용 개수를 초과했습니다",
    "details": {
      "couponType": "CART",
      "maxAllowed": 2,
      "attempted": 3
    }
  }
}
```

- `400 Bad Request`: 쿠폰 유효하지 않음
```json
{
  "success": false,
  "error": {
    "code": "INVALID_COUPON",
    "message": "유효하지 않은 쿠폰입니다",
    "details": {
      "couponId": 10,
      "reason": "EXPIRED"
    }
  }
}
```

- `409 Conflict`: 이미 결제 완료된 주문
```json
{
  "success": false,
  "error": {
    "code": "PAYMENT_ALREADY_COMPLETED",
    "message": "이미 결제가 완료된 주문입니다"
  }
}
```

---

## 5. 쿠폰 API

### 5.1 쿠폰 발급

선착순 쿠폰을 발급받습니다.

```http
POST /coupons/{couponId}/issue
```

#### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| couponId | Long | Yes | 쿠폰 ID |

#### Response (201 Created)
```json
{
  "success": true,
  "data": {
    "userCouponId": 1,
    "couponId": 10,
    "couponName": "신규 가입 10% 할인",
    "couponType": "CART",
    "discountType": "PERCENTAGE",
    "discountValue": 10,
    "minOrderAmount": 50000,
    "expiresAt": "2025-12-31T23:59:59Z",
    "issuedAt": "2025-10-31T10:00:00Z"
  },
  "message": "쿠폰이 발급되었습니다"
}
```

#### Error Responses
- `404 Not Found`: 쿠폰을 찾을 수 없음
- `409 Conflict`: 이미 발급받은 쿠폰
```json
{
  "success": false,
  "error": {
    "code": "COUPON_ALREADY_ISSUED",
    "message": "이미 발급받은 쿠폰입니다"
  }
}
```

- `409 Conflict`: 쿠폰 소진
```json
{
  "success": false,
  "error": {
    "code": "COUPON_SOLD_OUT",
    "message": "쿠폰이 모두 소진되었습니다"
  }
}
```

---

### 5.2 보유 쿠폰 조회

사용자가 보유한 쿠폰 목록을 조회합니다.

```http
GET /coupons/my
```

#### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| status | String | No | 쿠폰 상태 (AVAILABLE, USED, EXPIRED) |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "coupons": [
      {
        "userCouponId": 1,
        "couponId": 10,
        "couponName": "신규 가입 10% 할인",
        "couponType": "CART",
        "discountType": "PERCENTAGE",
        "discountValue": 10,
        "minOrderAmount": 50000,
        "applicableProductIds": null,
        "status": "AVAILABLE",
        "expiresAt": "2025-12-31T23:59:59Z",
        "issuedAt": "2025-10-31T10:00:00Z",
        "usedAt": null
      },
      {
        "userCouponId": 2,
        "couponId": 20,
        "couponName": "노트북 5만원 할인",
        "couponType": "CART_ITEM",
        "discountType": "FIXED",
        "discountValue": 50000,
        "minOrderAmount": 0,
        "applicableProductIds": [10, 11, 12],
        "status": "USED",
        "expiresAt": "2025-11-30T23:59:59Z",
        "issuedAt": "2025-10-25T10:00:00Z",
        "usedAt": "2025-10-30T15:30:00Z"
      }
    ],
    "totalCount": 2,
    "availableCount": 1
  }
}
```

---

### 5.3 쿠폰 사용 이력 조회

쿠폰 사용 이력을 조회합니다.

```http
GET /coupons/usage-history
```

#### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | Integer | No | 페이지 번호 (기본값: 1) |
| size | Integer | No | 페이지 크기 (기본값: 20) |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "usageHistory": [
      {
        "userCouponId": 2,
        "couponName": "노트북 5만원 할인",
        "orderId": 1001,
        "orderNumber": "ORD-20251031-1001",
        "discountAmount": 50000,
        "usedAt": "2025-10-30T15:30:00Z"
      }
    ],
    "pagination": {
      "currentPage": 1,
      "totalPages": 3,
      "totalItems": 50,
      "itemsPerPage": 20
    }
  }
}
```

---

## 6. 잔액 API

### 6.1 잔액 충전

사용자의 잔액을 충전합니다.

```http
POST /balance/charge
```

#### Request Body
```json
{
  "amount": 100000
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| amount | Integer | Yes | 충전 금액 (1,000원 이상) |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "transactionId": 1,
    "type": "CHARGE",
    "amount": 100000,
    "balance": {
      "before": 500000,
      "after": 600000
    },
    "createdAt": "2025-10-31T10:00:00Z"
  },
  "message": "잔액이 충전되었습니다"
}
```

#### Error Responses
- `400 Bad Request`: 최소 충전 금액 미달
```json
{
  "success": false,
  "error": {
    "code": "INVALID_CHARGE_AMOUNT",
    "message": "충전 금액은 1,000원 이상이어야 합니다",
    "details": {
      "minAmount": 1000,
      "requestedAmount": 500
    }
  }
}
```

---

### 6.2 잔액 조회

현재 사용자의 잔액을 조회합니다.

```http
GET /balance
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "balance": 600000,
    "lastUpdatedAt": "2025-10-31T10:00:00Z"
  }
}
```

---

### 6.3 잔액 사용 이력 조회

잔액 사용 및 충전 이력을 조회합니다.

```http
GET /balance/history
```

#### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | Integer | No | 페이지 번호 (기본값: 1) |
| size | Integer | No | 페이지 크기 (기본값: 20) |
| type | String | No | 거래 유형 (CHARGE, PAYMENT, REFUND) |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "transactions": [
      {
        "transactionId": 1,
        "type": "CHARGE",
        "amount": 100000,
        "balanceBefore": 500000,
        "balanceAfter": 600000,
        "description": "잔액 충전",
        "createdAt": "2025-10-31T10:00:00Z"
      },
      {
        "transactionId": 2,
        "type": "PAYMENT",
        "amount": -50000,
        "balanceBefore": 600000,
        "balanceAfter": 550000,
        "description": "주문 결제 (ORD-20251031-1001)",
        "relatedOrderId": 1001,
        "createdAt": "2025-10-31T11:00:00Z"
      }
    ],
    "pagination": {
      "currentPage": 1,
      "totalPages": 5,
      "totalItems": 100,
      "itemsPerPage": 20
    }
  }
}
```

---

## 7. 관리자 API

### 7.1 상품 등록

새로운 상품을 등록합니다.

```http
POST /admin/products
```

#### Request Body
```json
{
  "name": "노트북",
  "description": "고성능 노트북",
  "price": 1500000,
  "stock": 100,
  "maxQuantityPerCart": 5
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | String | Yes | 상품명 (최대 100자) |
| description | String | No | 상품 설명 (최대 2000자) |
| price | Integer | Yes | 가격 (0 이상) |
| stock | Integer | Yes | 초기 재고 (0 이상) |
| maxQuantityPerCart | Integer | No | 장바구니 제한 수량 |

#### Response (201 Created)
```json
{
  "success": true,
  "data": {
    "productId": 1,
    "name": "노트북",
    "description": "고성능 노트북",
    "price": 1500000,
    "stock": 100,
    "maxQuantityPerCart": 5,
    "status": "AVAILABLE",
    "createdAt": "2025-10-31T10:00:00Z"
  },
  "message": "상품이 등록되었습니다"
}
```

#### Error Responses
- `422 Unprocessable Entity`: 유효성 검증 실패
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값 검증에 실패했습니다",
    "details": {
      "name": "상품명은 필수입니다",
      "price": "가격은 0 이상이어야 합니다"
    }
  }
}
```

---

### 7.2 상품 수정

상품 정보를 수정합니다.

```http
PUT /admin/products/{productId}
```

#### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| productId | Long | Yes | 상품 ID |

#### Request Body
```json
{
  "name": "고성능 노트북",
  "description": "최신 고성능 노트북",
  "price": 1400000,
  "maxQuantityPerCart": 3
}
```

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "productId": 1,
    "name": "고성능 노트북",
    "description": "최신 고성능 노트북",
    "price": 1400000,
    "stock": 100,
    "maxQuantityPerCart": 3,
    "updatedAt": "2025-10-31T11:00:00Z"
  },
  "message": "상품 정보가 수정되었습니다"
}
```

---

### 7.3 재고 조정

상품의 재고를 조정합니다.

```http
POST /admin/products/{productId}/stock
```

#### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| productId | Long | Yes | 상품 ID |

#### Request Body
```json
{
  "type": "INCREASE",
  "quantity": 50,
  "reason": "신규 입고"
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| type | String | Yes | 조정 유형 (INCREASE, DECREASE, SET) |
| quantity | Integer | Yes | 수량 (양수) |
| reason | String | Yes | 조정 사유 |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "productId": 1,
    "stockBefore": 100,
    "stockAfter": 150,
    "adjustmentType": "INCREASE",
    "adjustmentQuantity": 50,
    "reason": "신규 입고",
    "adjustedAt": "2025-10-31T10:00:00Z",
    "adjustedBy": "admin@example.com"
  },
  "message": "재고가 조정되었습니다"
}
```

#### Error Responses
- `400 Bad Request`: 재고를 음수로 만들 수 없음
```json
{
  "success": false,
  "error": {
    "code": "INVALID_STOCK_ADJUSTMENT",
    "message": "재고는 음수가 될 수 없습니다",
    "details": {
      "currentStock": 10,
      "requestedDecrease": 20
    }
  }
}
```

---

### 7.4 쿠폰 생성

새로운 쿠폰을 생성합니다.

```http
POST /admin/coupons
```

#### Request Body
```json
{
  "name": "신규 가입 10% 할인",
  "type": "CART",
  "discountType": "PERCENTAGE",
  "discountValue": 10,
  "maxIssueCount": 1000,
  "minOrderAmount": 50000,
  "applicableProductIds": null,
  "expiresAt": "2025-12-31T23:59:59Z"
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | String | Yes | 쿠폰명 |
| type | String | Yes | 쿠폰 타입 (CART, CART_ITEM) |
| discountType | String | Yes | 할인 유형 (PERCENTAGE, FIXED) |
| discountValue | Integer | Yes | 할인 값 (퍼센트 또는 금액) |
| maxIssueCount | Integer | Yes | 최대 발급 수량 |
| minOrderAmount | Integer | No | 최소 주문 금액 |
| applicableProductIds | Long[] | Conditional | 적용 대상 상품 ID (CART_ITEM 타입 필수) |
| expiresAt | DateTime | Yes | 만료일시 |

#### Response (201 Created)
```json
{
  "success": true,
  "data": {
    "couponId": 10,
    "name": "신규 가입 10% 할인",
    "type": "CART",
    "discountType": "PERCENTAGE",
    "discountValue": 10,
    "maxIssueCount": 1000,
    "currentIssueCount": 0,
    "minOrderAmount": 50000,
    "expiresAt": "2025-12-31T23:59:59Z",
    "createdAt": "2025-10-31T10:00:00Z"
  },
  "message": "쿠폰이 생성되었습니다"
}
```

---

### 7.5 쿠폰 통계 조회

쿠폰별 사용 통계를 조회합니다.

```http
GET /admin/coupons/statistics
```

#### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| startDate | Date | No | 조회 시작일 |
| endDate | Date | No | 조회 종료일 |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "statistics": [
      {
        "couponId": 10,
        "couponName": "신규 가입 10% 할인",
        "couponType": "CART",
        "maxIssueCount": 1000,
        "issuedCount": 850,
        "usedCount": 720,
        "usageRate": 84.71,
        "totalDiscountAmount": 15000000,
        "averageDiscountAmount": 20833
      }
    ],
    "summary": {
      "totalIssuedCount": 850,
      "totalUsedCount": 720,
      "totalDiscountAmount": 15000000
    }
  }
}
```

---

### 7.6 주문 내역 조회 (관리자)

모든 주문 내역을 조회합니다.

```http
GET /admin/orders
```

#### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | Integer | No | 페이지 번호 (기본값: 1) |
| size | Integer | No | 페이지 크기 (기본값: 20) |
| status | String | No | 주문 상태 필터 |
| userId | Long | No | 사용자 ID 필터 |
| startDate | Date | No | 조회 시작일 |
| endDate | Date | No | 조회 종료일 |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "orders": [
      {
        "orderId": 1001,
        "orderNumber": "ORD-20251031-1001",
        "userId": 1,
        "userName": "홍길동",
        "userEmail": "hong@example.com",
        "status": "COMPLETED",
        "totalAmount": 3000000,
        "discountAmount": 100000,
        "finalAmount": 2900000,
        "itemCount": 2,
        "createdAt": "2025-10-31T10:00:00Z",
        "paidAt": "2025-10-31T10:05:00Z"
      }
    ],
    "pagination": {
      "currentPage": 1,
      "totalPages": 50,
      "totalItems": 1000,
      "itemsPerPage": 20
    },
    "summary": {
      "totalOrderCount": 1000,
      "totalSalesAmount": 500000000,
      "totalDiscountAmount": 50000000
    }
  }
}
```

---

### 7.7 인기 상품 통계 조회 (관리자)

인기 상품 상세 통계를 조회합니다.

```http
GET /admin/products/statistics/popular
```

#### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| days | Integer | No | 조회 기간 (일) (기본값: 3) |
| limit | Integer | No | 조회 개수 (기본값: 5) |

#### Response (200 OK)
```json
{
  "success": true,
  "data": {
    "statistics": [
      {
        "rank": 1,
        "productId": 10,
        "productName": "무선 이어폰",
        "price": 150000,
        "salesCount": 250,
        "totalSalesAmount": 37500000,
        "averageDailySales": 83,
        "stockLevel": 100
      }
    ],
    "period": {
      "startDate": "2025-10-28",
      "endDate": "2025-10-31",
      "days": 3
    },
    "updatedAt": "2025-10-31T00:00:00Z"
  }
}
```

---

## 에러 코드

### 공통 에러 코드

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| INVALID_REQUEST | 400 | 잘못된 요청 |
| UNAUTHORIZED | 401 | 인증 실패 |
| FORBIDDEN | 403 | 권한 없음 |
| NOT_FOUND | 404 | 리소스를 찾을 수 없음 |
| VALIDATION_ERROR | 422 | 유효성 검증 실패 |
| INTERNAL_SERVER_ERROR | 500 | 서버 내부 오류 |

### 상품 관련 에러 코드

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| PRODUCT_NOT_FOUND | 404 | 상품을 찾을 수 없음 |
| INSUFFICIENT_STOCK | 400 | 재고 부족 |
| EXCEED_MAX_QUANTITY | 400 | 장바구니 제한 수량 초과 |
| INVALID_STOCK_ADJUSTMENT | 400 | 잘못된 재고 조정 |

### 장바구니 관련 에러 코드

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| CART_ITEM_NOT_FOUND | 404 | 장바구니 아이템을 찾을 수 없음 |
| CART_EMPTY | 400 | 장바구니가 비어있음 |

### 주문 관련 에러 코드

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| ORDER_NOT_FOUND | 404 | 주문을 찾을 수 없음 |
| ORDER_NOT_AUTHORIZED | 403 | 주문 접근 권한 없음 |
| ORDER_ALREADY_PAID | 409 | 이미 결제 완료된 주문 |
| ORDER_CANNOT_CANCEL | 400 | 취소할 수 없는 주문 상태 |

### 결제 관련 에러 코드

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| PAYMENT_ALREADY_COMPLETED | 409 | 이미 결제 완료됨 |
| INSUFFICIENT_BALANCE | 400 | 잔액 부족 |
| INVALID_CHARGE_AMOUNT | 400 | 잘못된 충전 금액 |
| PAYMENT_FAILED | 500 | 결제 처리 실패 |

### 쿠폰 관련 에러 코드

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| COUPON_NOT_FOUND | 404 | 쿠폰을 찾을 수 없음 |
| COUPON_ALREADY_ISSUED | 409 | 이미 발급받은 쿠폰 |
| COUPON_SOLD_OUT | 409 | 쿠폰 소진 |
| COUPON_EXPIRED | 400 | 만료된 쿠폰 |
| COUPON_ALREADY_USED | 400 | 이미 사용된 쿠폰 |
| COUPON_NOT_OWNED | 403 | 쿠폰 소유자가 아님 |
| INVALID_COUPON | 400 | 유효하지 않은 쿠폰 |
| EXCEED_COUPON_LIMIT | 400 | 쿠폰 사용 개수 초과 |
| COUPON_NOT_APPLICABLE | 400 | 적용 불가능한 쿠폰 |
| MIN_ORDER_AMOUNT_NOT_MET | 400 | 최소 주문 금액 미달 |

---

## 부록

### A. 주문 상태 (Order Status)

| Status | Description |
|--------|-------------|
| PENDING_PAYMENT | 결제 대기 |
| COMPLETED | 주문 완료 |
| CANCELLED | 주문 취소 |
| REFUNDED | 환불 완료 |

### B. 쿠폰 타입 (Coupon Type)

| Type | Description |
|------|-------------|
| CART | 장바구니 전체 적용 |
| CART_ITEM | 특정 상품 적용 |

### C. 할인 타입 (Discount Type)

| Type | Description |
|------|-------------|
| PERCENTAGE | 퍼센트 할인 (%) |
| FIXED | 고정 금액 할인 (원) |

### D. 쿠폰 상태 (Coupon Status)

| Status | Description |
|--------|-------------|
| AVAILABLE | 사용 가능 |
| USED | 사용 완료 |
| EXPIRED | 만료됨 |

### E. 결제 수단 (Payment Method)

| Method | Description |
|--------|-------------|
| BALANCE | 잔액 결제 |

### F. 재고 조정 유형 (Stock Adjustment Type)

| Type | Description |
|------|-------------|
| INCREASE | 재고 증가 |
| DECREASE | 재고 감소 |
| SET | 재고 설정 |

### G. 거래 유형 (Transaction Type)

| Type | Description |
|------|-------------|
| CHARGE | 충전 |
| PAYMENT | 결제 |
| REFUND | 환불 |

---

## 변경 이력

| 버전 | 날짜 | 작성자 | 변경 내역 |
|------|------|--------|-----------|
| 1.0 | 2025-10-31 | System | 초기 API 명세서 작성 |
