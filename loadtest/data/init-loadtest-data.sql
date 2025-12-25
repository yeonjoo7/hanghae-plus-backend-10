-- =====================================================
-- 부하 테스트용 초기 데이터
-- =====================================================
-- 이 스크립트는 부하 테스트 전에 실행하여
-- 테스트에 필요한 대량의 데이터를 생성합니다.
--
-- 실행: mysql -u root -p ecommerce < init-loadtest-data.sql
-- =====================================================

-- 기존 데이터 삭제 (주의: 운영 환경에서 실행 금지)
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE user_coupons;
TRUNCATE TABLE order_items;
TRUNCATE TABLE orders;
TRUNCATE TABLE cart_items;
TRUNCATE TABLE carts;
TRUNCATE TABLE balance_transactions;
TRUNCATE TABLE users;
TRUNCATE TABLE coupons;
TRUNCATE TABLE stocks;
TRUNCATE TABLE products;
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 1. 사용자 데이터 생성 (1000명)
-- =====================================================
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS generate_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 1000 DO
        INSERT INTO users (id, email, state, type, name, phone, available_point, used_point, created_at, updated_at)
        VALUES (
            i,
            CONCAT('user', i, '@loadtest.com'),
            'NORMAL',
            'CUSTOMER',
            CONCAT('테스트사용자', i),
            CONCAT('010-', LPAD(i, 4, '0'), '-', LPAD(i, 4, '0')),
            0,  -- 초기 포인트 0 (테스트 시 충전)
            0,
            NOW(),
            NOW()
        );
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL generate_users();
DROP PROCEDURE IF EXISTS generate_users;

-- =====================================================
-- 2. 상품 데이터 생성 (충분한 재고)
-- =====================================================
INSERT INTO products (id, name, description, price, limited_quantity, state, created_at, updated_at) VALUES
(1, '노트북 Pro', '고성능 노트북 - 부하 테스트용', 1500000, 0, 'NORMAL', NOW(), NOW()),
(2, '무선 키보드', '블루투스 무선 키보드', 50000, 0, 'NORMAL', NOW(), NOW()),
(3, '게이밍 마우스', '고성능 게이밍 마우스', 30000, 0, 'NORMAL', NOW(), NOW()),
(4, '4K 모니터', '27인치 4K UHD 모니터', 300000, 0, 'NORMAL', NOW(), NOW()),
(5, '노이즈 캔슬링 헤드셋', '프리미엄 무선 헤드셋', 80000, 0, 'NORMAL', NOW(), NOW());

-- =====================================================
-- 3. 재고 데이터 생성 (대량 재고)
-- =====================================================
INSERT INTO stocks (id, product_id, available_quantity, sold_quantity, memo, created_at, updated_at) VALUES
(1, 1, 100000, 0, '노트북 10만개 재고', NOW(), NOW()),
(2, 2, 100000, 0, '키보드 10만개 재고', NOW(), NOW()),
(3, 3, 100000, 0, '마우스 10만개 재고', NOW(), NOW()),
(4, 4, 100000, 0, '모니터 10만개 재고', NOW(), NOW()),
(5, 5, 100000, 0, '헤드셋 10만개 재고', NOW(), NOW());

-- =====================================================
-- 4. 쿠폰 데이터 생성
-- =====================================================
-- 쿠폰 1: 일반 할인 쿠폰 (1000개 - 여유있게)
INSERT INTO coupons (id, name, state, discount_type, discount_value, min_order_amount, max_discount_amount,
                     total_quantity, issued_quantity, start_date, end_date, created_at, updated_at)
VALUES (1, '신규 회원 10% 할인', 'NORMAL', 'PERCENTAGE', 10, 50000, 20000, 1000, 0,
        NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW());

-- 쿠폰 2: 고정 할인 쿠폰 (500개)
INSERT INTO coupons (id, name, state, discount_type, discount_value, min_order_amount, max_discount_amount,
                     total_quantity, issued_quantity, start_date, end_date, created_at, updated_at)
VALUES (2, '5,000원 즉시 할인', 'NORMAL', 'FIXED_AMOUNT', 5000, 30000, NULL, 500, 0,
        NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW());

-- 쿠폰 3: 선착순 쿠폰 (100개 - 스파이크 테스트용)
INSERT INTO coupons (id, name, state, discount_type, discount_value, min_order_amount, max_discount_amount,
                     total_quantity, issued_quantity, start_date, end_date, created_at, updated_at)
VALUES (3, '선착순 20% 할인 쿠폰', 'NORMAL', 'PERCENTAGE', 20, 100000, 50000, 100, 0,
        NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), NOW(), NOW());

-- =====================================================
-- 5. 장바구니 생성 (각 사용자별)
-- =====================================================
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS generate_carts()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 1000 DO
        INSERT INTO carts (id, user_id, state, created_at, updated_at)
        VALUES (i, i, 'NORMAL', NOW(), NOW());
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL generate_carts();
DROP PROCEDURE IF EXISTS generate_carts;

-- =====================================================
-- 검증 쿼리
-- =====================================================
SELECT '=== 부하 테스트 데이터 생성 완료 ===' as message;
SELECT 'users' as table_name, COUNT(*) as count FROM users
UNION ALL
SELECT 'products', COUNT(*) FROM products
UNION ALL
SELECT 'stocks', COUNT(*) FROM stocks
UNION ALL
SELECT 'coupons', COUNT(*) FROM coupons
UNION ALL
SELECT 'carts', COUNT(*) FROM carts;

SELECT '=== 쿠폰 현황 ===' as message;
SELECT id, name, total_quantity, issued_quantity FROM coupons;
