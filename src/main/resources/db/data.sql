-- 초기 사용자 데이터
INSERT INTO users (id, email, name, balance) VALUES
('user-1', 'user1@example.com', '김철수', 100000),
('user-2', 'user2@example.com', '이영희', 50000),
('user-3', 'user3@example.com', '박민수', 200000);

-- 초기 상품 데이터
INSERT INTO products (id, name, price, stock, status) VALUES
('prod-1', '노트북', 1500000, 10, 'ACTIVE'),
('prod-2', '키보드', 50000, 50, 'ACTIVE'),
('prod-3', '마우스', 30000, 100, 'ACTIVE'),
('prod-4', '모니터', 300000, 20, 'ACTIVE'),
('prod-5', '헤드셋', 80000, 30, 'ACTIVE');

-- 상품 옵션 데이터
INSERT INTO product_options (id, product_id, name, additional_price, stock) VALUES
('opt-1-1', 'prod-1', '메모리 16GB 업그레이드', 200000, 5),
('opt-1-2', 'prod-1', 'SSD 1TB 업그레이드', 150000, 5),
('opt-2-1', 'prod-2', '백라이트 RGB', 20000, 20),
('opt-3-1', 'prod-3', '무선 버전', 10000, 50);

-- 쿠폰 데이터
INSERT INTO coupons (id, name, discount_type, discount_value, min_order_amount, max_discount_amount, total_quantity, issued_quantity, start_date, end_date) VALUES
('coupon-1', '신규 회원 10% 할인', 'PERCENTAGE', 10, 50000, 20000, 1000, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)),
('coupon-2', '5,000원 할인 쿠폰', 'FIXED_AMOUNT', 5000, 30000, NULL, 500, 0, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY)),
('coupon-3', '선착순 20% 할인', 'PERCENTAGE', 20, 100000, 50000, 100, 0, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY));

-- 인기 상품 캐시 데이터 (최근 3일간의 판매 데이터)
INSERT INTO popular_products_cache (product_id, sales_count, calculated_at, ranking, period_start, period_end) VALUES
('prod-1', 50, NOW(), 1, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
('prod-3', 45, NOW(), 2, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
('prod-2', 40, NOW(), 3, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
('prod-5', 35, NOW(), 4, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
('prod-4', 30, NOW(), 5, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW());