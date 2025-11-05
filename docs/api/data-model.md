# 이커머스 시스템 데이터 모델 설계

## 목차
1. [개요](#개요)
2. [ERD](#erd)
3. [엔티티 상세 설계](#엔티티-상세-설계)
4. [관계 정의](#관계-정의)
5. [인덱스 전략](#인덱스-전략)
6. [제약조건](#제약조건)

---

## 개요

본 문서는 이커머스 시스템의 데이터베이스 모델을 정의합니다.

### 설계 원칙
- **Soft Delete**: 데이터 삭제 시 물리적 삭제가 아닌 상태 변경 (state = 'DELETED')
- **No Foreign Key Constraints**: FK 제약조건 없이 애플리케이션 레벨에서 참조 무결성 관리
- **Audit Trail**: 모든 테이블에 생성일시, 수정일시 포함
- **정규화**: 3NF 기준 정규화

### 데이터베이스 정보
- **DBMS**: MySQL 8.0+
- **Character Set**: UTF-8
- **Collation**: utf8mb4_unicode_ci

### FK 제약조건 정책
- **물리적 FK 제약조건은 생성하지 않음**
- 참조 관계는 애플리케이션 레벨에서 관리
- 문서의 참조 관계는 논리적 관계를 나타냄

---

## ERD

### Entity Relationship Diagram

```
┌──────────────────┐
│      User        │
├──────────────────┤
│ PK id            │
│    email         │
│    state         │
│    type          │
│    name          │
│    phone         │
│    available_pt  │
│    used_point    │
│    created_at    │
│    updated_at    │
└────────┬─────────┘
         │
         ├─────────────────────────┬─────────────────────────┐
         │                         │                         │
    ┌────▼──────┐         ┌───────▼────────┐       ┌───────▼────────┐
    │UserCoupon │         │     Cart       │       │    Order       │
    ├───────────┤         ├────────────────┤       ├────────────────┤
    │PK id      │         │ PK id          │       │ PK id          │
    │   user_id │         │    user_id     │       │    user_id     │
    │   coupon  │         │    user_coupon │       │    user_coupon │
    │   state   │         │    state       │       │    cart_id     │
    │   issued  │         │    created_at  │       │    state       │
    │   used_at │         │    updated_at  │       │    amount      │
    │   expires │         └────────┬───────┘       │    discount_a  │
    │   created │                  │               │    total_amt   │
    │   updated │            ┌─────▼──────────┐    │    created_at  │
    └───────────┘            │   CartItem     │    │    updated_at  │
                             ├────────────────┤    └────────┬───────┘
                             │ PK id          │             │
                             │    cart_id     │       ┌─────▼──────────┐
                             │    product_id  │       │   OrderItem    │
                             │    product_opt │       ├────────────────┤
                             │    user_coupon │       │ PK id          │
                             │    state       │       │    order_id    │
                             │    quantity    │       │    product_id  │
                             │    created_at  │       │    product_opt │
                             │    updated_at  │       │    user_coupon │
                             └────────────────┘       │    state       │
                                                      │    price       │
┌──────────────────┐                                 │    quantity    │
│     Product      │                                 │    discount_a  │
├──────────────────┤                                 │    total_amt   │
│ PK id            │                                 │    created_at  │
│    state         │                                 │    updated_at  │
│    name          │                                 └────────────────┘
│    price         │                                          │
│    limited_qty   │                                    ┌─────▼─────────┐
│    created_at    │                                    │   Payment     │
│    updated_at    │                                    ├───────────────┤
└────────┬─────────┘                                    │ PK id         │
         │                                              │    order_id   │
         ├───────────────────┐                          │    state      │
    ┌────▼──────────┐   ┌────▼──────┐                  │    method     │
    │ProductOption  │   │   Stock   │                  │    paid_amt   │
    ├───────────────┤   ├───────────┤                  │    expires_at │
    │ PK id         │   │ PK id     │                  │    created_at │
    │    product_id │   │    prod_id│                  │    updated_at │
    │    state      │   │    prod_op│                  └───────────────┘
    │    price      │   │    avail  │
    │    created_at │   │    sold   │
    │    updated_at │   │    memo   │
    └───────────────┘   │    created│
                        │    updated│
┌──────────────────┐   └───────────┘
│     Coupon       │
├──────────────────┤
│ PK id            │
│    name          │
│    type          │
│    state         │
│    discount_rate │
│    discount_price│
│    total_qty     │
│    issued_qty    │
│    begin_date    │
│    end_date      │
│    created_at    │
│    updated_at    │
└──────────────────┘
```

---

## 엔티티 상세 설계

### 1. User (사용자)

사용자 계정 및 포인트 정보를 관리합니다.

```sql
CREATE TABLE `user` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '사용자 ID',
    `email` VARCHAR(255) NOT NULL COMMENT '이메일 (로그인 ID)',
    `state` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '상태',
    `type` VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' COMMENT '사용자 유형',
    `name` VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    `phone` VARCHAR(20) COMMENT '연락처',
    `available_point` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '사용 가능 잔액 (원)',
    `used_point` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '사용한 포인트',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    UNIQUE KEY `uk_user_email` (`email`),
    INDEX `idx_user_state` (`state`),
    INDEX `idx_user_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 사용자 ID (PK) |
| email | VARCHAR(255) | NO | - | 이메일 (로그인 ID, UNIQUE) |
| state | VARCHAR(20) | NO | 'NORMAL' | 상태 |
| type | VARCHAR(20) | NO | 'CUSTOMER' | 사용자 유형 |
| name | VARCHAR(100) | NO | - | 사용자 이름 |
| phone | VARCHAR(20) | YES | NULL | 연락처 |
| available_point | INT UNSIGNED | NO | 0 | 사용 가능 잔액 (원) |
| used_point | INT UNSIGNED | NO | 0 | 사용한 포인트 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: NORMAL, INACTIVE, DELETED
- **type**: CUSTOMER, ADMIN

#### 비즈니스 로직
- **잔액 관리**: available_point는 사용자의 충전식 잔액 (포인트)를 의미
- 총 포인트 = available_point + used_point
- 잔액 충전 시: available_point 증가
- 결제 시: available_point 감소, used_point 증가
- 환불 시: available_point 증가, used_point 감소
- available_point는 항상 0 이상이어야 함 (음수 불가)
- API 명세서에서는 "잔액(balance)"으로 표현되지만, DB에서는 포인트 시스템으로 구현

---

### 2. Product (상품)

상품 기본 정보를 관리합니다.

```sql
CREATE TABLE `product` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '상품 ID',
    `state` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '상태',
    `name` VARCHAR(200) NOT NULL COMMENT '상품명',
    `description` TEXT COMMENT '상품 설명',
    `price` INT UNSIGNED NOT NULL COMMENT '기본 가격',
    `limited_quantity` INT UNSIGNED COMMENT '제한 수량 (1인당 구매 제한)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_product_state` (`state`),
    INDEX `idx_product_state_price` (`state`, `price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 상품 ID (PK) |
| state | VARCHAR(20) | NO | 'NORMAL' | 상태 |
| name | VARCHAR(200) | NO | - | 상품명 |
| description | TEXT | YES | NULL | 상품 설명 |
| price | INT UNSIGNED | NO | - | 기본 가격 (원) |
| limited_quantity | INT UNSIGNED | YES | NULL | 제한 수량 (1인당) |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: NORMAL, OUT_OF_STOCK, DISCONTINUED, DELETED

---

### 3. ProductOption (상품 옵션)

상품의 세부 옵션 정보를 관리합니다.

```sql
CREATE TABLE `product_option` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '상품 옵션 ID',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '상품 ID (논리적 참조)',
    `state` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '상태',
    `price` INT UNSIGNED NOT NULL COMMENT '옵션 가격',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_product_option_product` (`product_id`),
    INDEX `idx_product_option_state` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 옵션';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 상품 옵션 ID (PK) |
| product_id | BIGINT UNSIGNED | NO | - | 상품 ID (논리적 FK) |
| state | VARCHAR(20) | NO | 'NORMAL' | 상태 |
| price | INT UNSIGNED | NO | - | 옵션 가격 (원) |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: NORMAL, DISCONTINUED, DELETED

#### 참조 관계 (논리적)
- `product_id` → `product.id`

---

### 4. Stock (재고)

상품 및 옵션별 재고를 관리합니다.

```sql
CREATE TABLE `stock` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '재고 ID',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '상품 ID (논리적 참조)',
    `product_option_id` BIGINT UNSIGNED COMMENT '상품 옵션 ID (논리적 참조)',
    `available_quantity` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '사용 가능 재고',
    `sold_quantity` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '판매된 수량',
    `memo` VARCHAR(500) COMMENT '메모',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    UNIQUE KEY `uk_stock_product_option` (`product_id`, `product_option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='재고';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 재고 ID (PK) |
| product_id | BIGINT UNSIGNED | NO | - | 상품 ID (논리적 FK) |
| product_option_id | BIGINT UNSIGNED | YES | NULL | 상품 옵션 ID (논리적 FK) |
| available_quantity | INT UNSIGNED | NO | 0 | 사용 가능 재고 |
| sold_quantity | INT UNSIGNED | NO | 0 | 판매된 수량 |
| memo | VARCHAR(500) | YES | NULL | 메모 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### 참조 관계 (논리적)
- `product_id` → `product.id`
- `product_option_id` → `product_option.id`

#### 비즈니스 로직
- 총 재고 = available_quantity + sold_quantity
- 주문 시: available_quantity 감소, sold_quantity 증가
- 환불 시: available_quantity 증가, sold_quantity 감소

---

### 5. Coupon (쿠폰)

쿠폰 템플릿 정보를 관리합니다.

```sql
CREATE TABLE `coupon` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '쿠폰 ID',
    `name` VARCHAR(200) NOT NULL COMMENT '쿠폰명',
    `state` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '상태',
    `discount_rate` INT UNSIGNED COMMENT '할인율 (%)',
    `discount_price` INT UNSIGNED COMMENT '할인 금액 (원)',
    `total_quantity` INT UNSIGNED NOT NULL COMMENT '총 발급 가능 수량',
    `issued_quantity` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '발급된 수량',
    `begin_date` DATETIME NOT NULL COMMENT '사용 시작일시',
    `end_date` DATETIME NOT NULL COMMENT '사용 종료일시',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_coupon_state` (`state`),
    INDEX `idx_coupon_date` (`begin_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='쿠폰';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 쿠폰 ID (PK) |
| name | VARCHAR(200) | NO | - | 쿠폰명 |
| state | VARCHAR(20) | NO | 'NORMAL' | 상태 |
| discount_rate | INT UNSIGNED | YES | NULL | 할인율 (%) |
| discount_price | INT UNSIGNED | YES | NULL | 할인 금액 (원) |
| total_quantity | INT UNSIGNED | NO | - | 총 발급 가능 수량 |
| issued_quantity | INT UNSIGNED | NO | 0 | 발급된 수량 |
| begin_date | DATETIME | NO | - | 사용 시작일시 |
| end_date | DATETIME | NO | - | 사용 종료일시 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: NORMAL, EXPIRED, DISCONTINUED, DELETED

#### 비즈니스 로직
- 쿠폰은 장바구니 전체에 적용됨
- discount_rate와 discount_price 중 하나만 값을 가짐
- issued_quantity <= total_quantity
- 발급 시 issued_quantity 증가

---

### 6. UserCoupon (사용자 쿠폰)

사용자가 발급받은 쿠폰을 관리합니다.

```sql
CREATE TABLE `user_coupon` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '사용자 쿠폰 ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '사용자 ID (논리적 참조)',
    `coupon_id` BIGINT UNSIGNED NOT NULL COMMENT '쿠폰 ID (논리적 참조)',
    `state` VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '상태',
    `issued_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급일시',
    `used_at` DATETIME COMMENT '사용일시',
    `expires_at` DATETIME NOT NULL COMMENT '만료일시',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_user_coupon_user` (`user_id`),
    INDEX `idx_user_coupon_coupon` (`coupon_id`),
    INDEX `idx_user_coupon_user_state` (`user_id`, `state`),
    INDEX `idx_user_coupon_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 쿠폰';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 사용자 쿠폰 ID (PK) |
| user_id | BIGINT UNSIGNED | NO | - | 사용자 ID (논리적 FK) |
| coupon_id | BIGINT UNSIGNED | NO | - | 쿠폰 ID (논리적 FK) |
| state | VARCHAR(20) | NO | 'AVAILABLE' | 상태 |
| issued_at | DATETIME | NO | CURRENT_TIMESTAMP | 발급일시 |
| used_at | DATETIME | YES | NULL | 사용일시 |
| expires_at | DATETIME | NO | - | 만료일시 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: AVAILABLE, USED, EXPIRED, DELETED

#### 참조 관계 (논리적)
- `user_id` → `user.id`
- `coupon_id` → `coupon.id`

---

### 7. Cart (장바구니)

사용자별 장바구니를 관리합니다.

```sql
CREATE TABLE `cart` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '장바구니 ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '사용자 ID (논리적 참조)',
    `user_coupon_id` BIGINT UNSIGNED COMMENT '적용된 사용자 쿠폰 ID (논리적 참조)',
    `state` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '상태',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_cart_user` (`user_id`),
    INDEX `idx_cart_user_state` (`user_id`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='장바구니';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 장바구니 ID (PK) |
| user_id | BIGINT UNSIGNED | NO | - | 사용자 ID (논리적 FK) |
| user_coupon_id | BIGINT UNSIGNED | YES | NULL | 적용된 사용자 쿠폰 ID (논리적 FK) |
| state | VARCHAR(20) | NO | 'NORMAL' | 상태 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: NORMAL, ORDERED, DELETED

#### 참조 관계 (논리적)
- `user_id` → `user.id`
- `user_coupon_id` → `user_coupon.id`

---

### 8. CartItem (장바구니 상품)

장바구니에 담긴 상품들을 관리합니다.

```sql
CREATE TABLE `cart_item` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '장바구니 아이템 ID',
    `cart_id` BIGINT UNSIGNED NOT NULL COMMENT '장바구니 ID (논리적 참조)',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '상품 ID (논리적 참조)',
    `product_option_id` BIGINT UNSIGNED COMMENT '상품 옵션 ID (논리적 참조)',
    `state` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '상태',
    `quantity` INT UNSIGNED NOT NULL COMMENT '수량',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_cart_item_cart` (`cart_id`),
    INDEX `idx_cart_item_product` (`product_id`),
    INDEX `idx_cart_item_cart_state` (`cart_id`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='장바구니 상품';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 장바구니 아이템 ID (PK) |
| cart_id | BIGINT UNSIGNED | NO | - | 장바구니 ID (논리적 FK) |
| product_id | BIGINT UNSIGNED | NO | - | 상품 ID (논리적 FK) |
| product_option_id | BIGINT UNSIGNED | YES | NULL | 상품 옵션 ID (논리적 FK) |
| state | VARCHAR(20) | NO | 'NORMAL' | 상태 |
| quantity | INT UNSIGNED | NO | - | 수량 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: NORMAL, DELETED

#### 참조 관계 (논리적)
- `cart_id` → `cart.id`
- `product_id` → `product.id`
- `product_option_id` → `product_option.id`

---

### 9. Order (주문)

주문 정보를 관리합니다.

```sql
CREATE TABLE `order` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '주문 ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '사용자 ID (논리적 참조)',
    `user_coupon_id` BIGINT UNSIGNED COMMENT '적용된 사용자 쿠폰 ID (논리적 참조)',
    `cart_id` BIGINT UNSIGNED COMMENT '장바구니 ID (논리적 참조)',
    `order_number` VARCHAR(50) NOT NULL COMMENT '주문 번호',
    `state` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '상태',
    `amount` INT UNSIGNED NOT NULL COMMENT '총 상품 금액',
    `discount_amount` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '총 할인 금액',
    `total_amount` INT UNSIGNED NOT NULL COMMENT '최종 결제 금액',
    `recipient_name` VARCHAR(100) NOT NULL COMMENT '수령인 이름',
    `recipient_phone` VARCHAR(20) NOT NULL COMMENT '수령인 연락처',
    `zip_code` VARCHAR(10) NOT NULL COMMENT '우편번호',
    `address` VARCHAR(500) NOT NULL COMMENT '주소',
    `detail_address` VARCHAR(500) COMMENT '상세 주소',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    UNIQUE KEY `uk_order_number` (`order_number`),
    INDEX `idx_order_user` (`user_id`),
    INDEX `idx_order_state` (`state`),
    INDEX `idx_order_user_created` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 주문 ID (PK) |
| user_id | BIGINT UNSIGNED | NO | - | 사용자 ID (논리적 FK) |
| user_coupon_id | BIGINT UNSIGNED | YES | NULL | 적용된 사용자 쿠폰 ID (논리적 FK) |
| cart_id | BIGINT UNSIGNED | YES | NULL | 장바구니 ID (논리적 FK) |
| order_number | VARCHAR(50) | NO | - | 주문 번호 (고유) |
| state | VARCHAR(20) | NO | 'NORMAL' | 상태 |
| amount | INT UNSIGNED | NO | - | 총 상품 금액 (원) |
| discount_amount | INT UNSIGNED | NO | 0 | 총 할인 금액 (원) |
| total_amount | INT UNSIGNED | NO | - | 최종 결제 금액 (원) |
| recipient_name | VARCHAR(100) | NO | - | 수령인 이름 |
| recipient_phone | VARCHAR(20) | NO | - | 수령인 연락처 |
| zip_code | VARCHAR(10) | NO | - | 우편번호 |
| address | VARCHAR(500) | NO | - | 주소 |
| detail_address | VARCHAR(500) | YES | NULL | 상세 주소 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: NORMAL, CANCELLED, REFUNDED, DELETED

#### 참조 관계 (논리적)
- `user_id` → `user.id`
- `user_coupon_id` → `user_coupon.id`
- `cart_id` → `cart.id`

#### 비즈니스 로직
- total_amount = amount - discount_amount

---

### 10. OrderItem (주문 상품)

주문에 포함된 상품들을 관리합니다.

```sql
CREATE TABLE `order_item` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '주문 아이템 ID',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '주문 ID (논리적 참조)',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '상품 ID (논리적 참조)',
    `product_option_id` BIGINT UNSIGNED COMMENT '상품 옵션 ID (논리적 참조)',
    `state` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '상태',
    `price` INT UNSIGNED NOT NULL COMMENT '상품 단가',
    `quantity` INT UNSIGNED NOT NULL COMMENT '수량',
    `discount_amount` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '할인 금액',
    `total_amount` INT UNSIGNED NOT NULL COMMENT '총 금액',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_order_item_order` (`order_id`),
    INDEX `idx_order_item_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문 상품';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 주문 아이템 ID (PK) |
| order_id | BIGINT UNSIGNED | NO | - | 주문 ID (논리적 FK) |
| product_id | BIGINT UNSIGNED | NO | - | 상품 ID (논리적 FK) |
| product_option_id | BIGINT UNSIGNED | YES | NULL | 상품 옵션 ID (논리적 FK) |
| state | VARCHAR(20) | NO | 'NORMAL' | 상태 |
| price | INT UNSIGNED | NO | - | 상품 단가 (원) |
| quantity | INT UNSIGNED | NO | - | 수량 |
| discount_amount | INT UNSIGNED | NO | 0 | 할인 금액 (원) |
| total_amount | INT UNSIGNED | NO | - | 총 금액 (원) |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: NORMAL, CANCELLED, DELETED

#### 참조 관계 (논리적)
- `order_id` → `order.id`
- `product_id` → `product.id`
- `product_option_id` → `product_option.id`

#### 비즈니스 로직
- total_amount = (price * quantity) - discount_amount

---

### 11. Payment (결제)

결제 정보를 관리합니다.

```sql
CREATE TABLE `payment` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '결제 ID',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '주문 ID (논리적 참조)',
    `state` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태',
    `method` VARCHAR(20) NOT NULL COMMENT '결제 수단',
    `paid_amount` INT UNSIGNED NOT NULL COMMENT '결제 금액',
    `expires_at` DATETIME COMMENT '결제 만료일시',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_payment_order` (`order_id`),
    INDEX `idx_payment_state` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='결제';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 결제 ID (PK) |
| order_id | BIGINT UNSIGNED | NO | - | 주문 ID (논리적 FK) |
| state | VARCHAR(20) | NO | 'PENDING' | 상태 |
| method | VARCHAR(20) | NO | - | 결제 수단 |
| paid_amount | INT UNSIGNED | NO | - | 결제 금액 (원) |
| expires_at | DATETIME | YES | NULL | 결제 만료일시 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **state**: PENDING, COMPLETED, FAILED, CANCELLED, REFUNDED, DELETED
- **method**: POINT, CARD, BANK_TRANSFER

#### 참조 관계 (논리적)
- `order_id` → `order.id`

---

### 12. BalanceTransaction (잔액 거래 내역)

잔액 충전, 사용, 환불 내역을 관리합니다.

```sql
CREATE TABLE `balance_transaction` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '거래 ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '사용자 ID (논리적 참조)',
    `order_id` BIGINT UNSIGNED COMMENT '주문 ID (논리적 참조)',
    `type` VARCHAR(20) NOT NULL COMMENT '거래 유형',
    `amount` BIGINT NOT NULL COMMENT '거래 금액',
    `balance_before` BIGINT NOT NULL COMMENT '거래 전 잔액',
    `balance_after` BIGINT NOT NULL COMMENT '거래 후 잔액',
    `description` VARCHAR(500) COMMENT '거래 설명',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_balance_tx_user` (`user_id`),
    INDEX `idx_balance_tx_order` (`order_id`),
    INDEX `idx_balance_tx_type` (`type`),
    INDEX `idx_balance_tx_user_created` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='잔액 거래 내역';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 거래 ID (PK) |
| user_id | BIGINT UNSIGNED | NO | - | 사용자 ID (논리적 FK) |
| order_id | BIGINT UNSIGNED | YES | NULL | 주문 ID (논리적 FK) |
| type | VARCHAR(20) | NO | - | 거래 유형 |
| amount | BIGINT | NO | - | 거래 금액 (양수/음수) |
| balance_before | BIGINT | NO | - | 거래 전 잔액 |
| balance_after | BIGINT | NO | - | 거래 후 잔액 |
| description | VARCHAR(500) | YES | NULL | 거래 설명 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### Enum Values
- **type**: CHARGE, PAYMENT, REFUND

#### 참조 관계 (논리적)
- `user_id` → `user.id`
- `order_id` → `order.id`

---

### 13. PopularProduct (인기 상품 통계)

인기 상품 통계를 관리합니다.

```sql
CREATE TABLE `popular_product` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '인기 상품 ID',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '상품 ID (논리적 참조)',
    `rank` INT UNSIGNED NOT NULL COMMENT '순위',
    `sales_count` INT UNSIGNED NOT NULL COMMENT '판매 수량',
    `sales_amount` BIGINT UNSIGNED NOT NULL COMMENT '판매 금액',
    `period_start` DATE NOT NULL COMMENT '집계 시작일',
    `period_end` DATE NOT NULL COMMENT '집계 종료일',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX `idx_popular_product` (`product_id`),
    INDEX `idx_popular_rank` (`rank`),
    INDEX `idx_popular_period` (`period_start`, `period_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='인기 상품 통계';
```

#### 속성
| Column | Type | Null | Default | Description |
|--------|------|------|---------|-------------|
| id | BIGINT UNSIGNED | NO | AUTO_INCREMENT | 인기 상품 ID (PK) |
| product_id | BIGINT UNSIGNED | NO | - | 상품 ID (논리적 FK) |
| rank | INT UNSIGNED | NO | - | 순위 (1-5) |
| sales_count | INT UNSIGNED | NO | - | 판매 수량 |
| sales_amount | BIGINT UNSIGNED | NO | - | 판매 금액 |
| period_start | DATE | NO | - | 집계 시작일 |
| period_end | DATE | NO | - | 집계 종료일 |
| created_at | DATETIME | NO | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | DATETIME | NO | CURRENT_TIMESTAMP | 수정일시 |

#### 참조 관계 (논리적)
- `product_id` → `product.id`

#### 비즈니스 로직
- 최근 3일간 판매 데이터 기반으로 집계
- 상위 5개 상품만 저장
- 일정 주기로 자동 갱신

---

## 관계 정의

### 논리적 관계 (FK 제약조건 없음)

#### User 관련
- `User` 1 : N `UserCoupon`
- `User` 1 : N `Cart`
- `User` 1 : N `Order`
- `User` 1 : N `BalanceTransaction`

#### Product 관련
- `Product` 1 : N `ProductOption`
- `Product` 1 : 1 `Stock` (옵션이 없는 경우)
- `ProductOption` 1 : 1 `Stock`
- `Product` 1 : N `CartItem`
- `Product` 1 : N `OrderItem`
- `Product` 1 : N `PopularProduct`

#### Coupon 관련
- `Coupon` 1 : N `UserCoupon`
- `UserCoupon` 1 : 1 `Cart`
- `UserCoupon` 1 : 1 `Order`

#### Cart 관련
- `Cart` 1 : N `CartItem`
- `Cart` 1 : 1 `Order` (주문 생성 시)

#### Order 관련
- `Order` 1 : N `OrderItem`
- `Order` 1 : 1 `Payment`
- `Order` 1 : N `BalanceTransaction`

---

## 인덱스 전략

### Primary Key Indexes
모든 테이블의 Primary Key에 자동으로 클러스터드 인덱스 생성

### 단일 컬럼 인덱스

```sql
-- User
CREATE INDEX idx_user_state ON `user`(state);
CREATE INDEX idx_user_type ON `user`(type);

-- Product
CREATE INDEX idx_product_state ON product(state);

-- ProductOption
CREATE INDEX idx_product_option_product ON product_option(product_id);
CREATE INDEX idx_product_option_state ON product_option(state);

-- Coupon
CREATE INDEX idx_coupon_state ON coupon(state);

-- UserCoupon
CREATE INDEX idx_user_coupon_user ON user_coupon(user_id);
CREATE INDEX idx_user_coupon_coupon ON user_coupon(coupon_id);
CREATE INDEX idx_user_coupon_expires ON user_coupon(expires_at);

-- Cart
CREATE INDEX idx_cart_user ON cart(user_id);

-- CartItem
CREATE INDEX idx_cart_item_cart ON cart_item(cart_id);
CREATE INDEX idx_cart_item_product ON cart_item(product_id);

-- Order
CREATE INDEX idx_order_user ON `order`(user_id);
CREATE INDEX idx_order_state ON `order`(state);

-- OrderItem
CREATE INDEX idx_order_item_order ON order_item(order_id);
CREATE INDEX idx_order_item_product ON order_item(product_id);

-- Payment
CREATE INDEX idx_payment_order ON payment(order_id);
CREATE INDEX idx_payment_state ON payment(state);

-- BalanceTransaction
CREATE INDEX idx_balance_tx_user ON balance_transaction(user_id);
CREATE INDEX idx_balance_tx_order ON balance_transaction(order_id);
CREATE INDEX idx_balance_tx_type ON balance_transaction(type);

-- PopularProduct
CREATE INDEX idx_popular_product ON popular_product(product_id);
CREATE INDEX idx_popular_rank ON popular_product(rank);
```

### 복합 인덱스

```sql
-- Product: 상태별 가격 정렬 조회
CREATE INDEX idx_product_state_price ON product(state, price);

-- Coupon: 사용 기간별 쿠폰 조회
CREATE INDEX idx_coupon_date ON coupon(begin_date, end_date);

-- UserCoupon: 사용자별 상태별 쿠폰 조회
CREATE INDEX idx_user_coupon_user_state ON user_coupon(user_id, state);

-- Cart: 사용자별 상태별 장바구니 조회
CREATE INDEX idx_cart_user_state ON cart(user_id, state);

-- CartItem: 장바구니별 상태별 아이템 조회
CREATE INDEX idx_cart_item_cart_state ON cart_item(cart_id, state);

-- Order: 사용자별 최신 주문 조회
CREATE INDEX idx_order_user_created ON `order`(user_id, created_at DESC);

-- BalanceTransaction: 사용자별 최신 거래 내역 조회
CREATE INDEX idx_balance_tx_user_created ON balance_transaction(user_id, created_at DESC);

-- PopularProduct: 기간별 인기 상품 조회
CREATE INDEX idx_popular_period ON popular_product(period_start, period_end);
```

### Unique 인덱스

```sql
-- User: 이메일 중복 방지
CREATE UNIQUE INDEX uk_user_email ON `user`(email);

-- Stock: 상품과 옵션 조합 중복 방지
CREATE UNIQUE INDEX uk_stock_product_option ON stock(product_id, product_option_id);

-- Order: 주문 번호 중복 방지
CREATE UNIQUE INDEX uk_order_number ON `order`(order_number);
```

---

## 제약조건

### Primary Key 제약조건
- 모든 테이블에 `BIGINT UNSIGNED AUTO_INCREMENT` 타입의 Primary Key 정의

### Foreign Key 제약조건
- **물리적 FK 제약조건은 생성하지 않음**
- 애플리케이션 레벨에서 참조 무결성 관리
- 문서의 "논리적 참조" 표시는 개발 가이드용

### Unique 제약조건
- `user.email`: 이메일 중복 방지
- `stock.product_option_id`: 옵션별 재고 중복 방지

### Default 값
- `state` 컬럼: 기본값 설정 (NORMAL, PENDING 등)
- `created_at`: CURRENT_TIMESTAMP
- `updated_at`: CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
- 수량/금액 필드: 0 또는 적절한 기본값

### Soft Delete 정책
- 모든 주요 테이블에 `state` 컬럼 포함
- 삭제 시 `state = 'DELETED'`로 변경
- 조회 시 `WHERE state != 'DELETED'` 조건 추가
- 물리적 삭제는 정책에 따라 별도 배치 작업으로 수행

---

## 쿠폰 적용 로직

### 장바구니 쿠폰
- 장바구니 전체에 할인 적용
- `cart.user_coupon_id`에 저장
- `order.user_coupon_id`에도 동일하게 저장
- 최대 1개까지 사용 가능 (비즈니스 로직)
- 할인은 order 레벨에서 계산되어 `order.discount_amount`에 반영됨

---

## 성능 최적화 전략

### 1. 읽기 성능 최적화
- 자주 조회되는 컬럼에 인덱스 생성
- 복합 인덱스 활용으로 커버링 인덱스 구성
- Soft Delete 조건을 인덱스에 포함

### 2. 쓰기 성능 최적화
- FK 제약조건 없음으로 삽입/수정 성능 향상
- 인덱스 최소화 (필요한 인덱스만 유지)
- 배치 작업은 Off-Peak 시간에 수행

### 3. 동시성 제어
- **낙관적 락**: 애플리케이션 레벨에서 version 컬럼 관리
- **비관적 락**: 재고 차감 시 `SELECT ... FOR UPDATE`
- **분산 락**: 쿠폰 발급 시 Redis 활용

### 4. 파티셔닝
```sql
-- Order: 생성일 기준 월별 파티셔닝
ALTER TABLE `order`
PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202510 VALUES LESS THAN (202511),
    PARTITION p202511 VALUES LESS THAN (202512),
    -- ...
);
```

---

## 데이터 보존 정책

### 영구 보관
- `order`, `order_item`: 주문 데이터 (최소 3년)
- `payment`: 결제 데이터 (최소 3년)
- `user_coupon`: 쿠폰 사용 이력 (영구)

### 보관 기간 제한
- `cart`, `cart_item`: 30일 후 자동 삭제 (배치)
- `stock`: 재고 변경 이력 (별도 history 테이블 권장)

---

## 초기 데이터 생성 스크립트

```sql
-- 데이터베이스 생성
CREATE DATABASE ecommerce DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ecommerce;

-- 모든 테이블 생성 (위 스키마 순서대로)
-- ...

-- 샘플 사용자 생성 (잔액 100,000원 충전된 고객, 관리자)
INSERT INTO `user` (email, state, type, name, phone, available_point, used_point) VALUES
('customer@example.com', 'NORMAL', 'CUSTOMER', '홍길동', '010-1234-5678', 100000, 0),
('admin@example.com', 'NORMAL', 'ADMIN', '관리자', '010-9999-9999', 0, 0);

-- 샘플 상품 생성
INSERT INTO product (state, name, description, price, limited_quantity) VALUES
('NORMAL', '노트북', '고성능 노트북', 1500000, 5),
('NORMAL', '마우스', '무선 마우스', 30000, NULL),
('NORMAL', '키보드', '기계식 키보드', 150000, 10);

-- 샘플 재고 생성
INSERT INTO stock (product_id, product_option_id, available_quantity, sold_quantity) VALUES
(1, NULL, 100, 0),
(2, NULL, 500, 0),
(3, NULL, 200, 0);

-- 샘플 쿠폰 생성
INSERT INTO coupon (name, state, discount_rate, discount_price, total_quantity, issued_quantity, begin_date, end_date) VALUES
('신규 가입 10% 할인', 'NORMAL', 10, NULL, 1000, 0, '2025-10-01 00:00:00', '2025-12-31 23:59:59'),
('장바구니 50,000원 할인', 'NORMAL', NULL, 50000, 500, 0, '2025-10-01 00:00:00', '2025-11-30 23:59:59');
```

---

## 변경 이력

| 버전 | 날짜 | 작성자 | 변경 내역 |
|------|------|--------|-----------|
| 1.0 | 2025-10-31 | System | 초기 데이터 모델 설계 |
| 1.1 | 2025-10-31 | System | 참고 ERD 기반 수정 (ProductOption, Stock, FK 제거 등) |
| 1.2 | 2025-10-31 | System | API 명세서 기반 누락 필드 및 테이블 추가<br>- Product: description 필드 추가<br>- Order: order_number, 배송 정보 필드 추가<br>- BalanceTransaction 테이블 추가<br>- PopularProduct 테이블 추가 |
| 1.3 | 2025-10-31 | System | User 테이블 잔액/포인트 필드 설명 명확화<br>- available_point: "사용 가능 잔액 (원)"<br>- used_point: "사용한 포인트"<br>- 비즈니스 로직 상세 설명 추가 |
| 1.4 | 2025-11-06 | System | 쿠폰 시스템 단순화<br>- Coupon: type 필드 제거 (장바구니 전체 할인만 지원)<br>- CartItem, OrderItem: user_coupon_id 필드 제거 |
| 1.5 | 2025-11-06 | System | Stock 테이블 유니크 키 변경<br>- UNIQUE KEY (product_id, product_option_id)로 복합 키 적용<br>- 상품별로 여러 재고 레코드 관리 가능 |
| 1.6 | 2025-11-06 | System | Stock 테이블 중복 인덱스 제거<br>- idx_stock_product 제거 (복합 유니크 키의 leftmost prefix 활용) |
