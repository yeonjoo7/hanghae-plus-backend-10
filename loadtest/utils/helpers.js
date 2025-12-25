import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, getAuthHeaders, TEST_PRODUCT_IDS } from './config.js';

// 커스텀 메트릭
export const couponIssuedCounter = new Counter('coupons_issued');
export const couponFailedCounter = new Counter('coupons_failed');
export const orderCreatedCounter = new Counter('orders_created');
export const paymentSuccessCounter = new Counter('payments_success');
export const paymentFailedCounter = new Counter('payments_failed');
export const errorRate = new Rate('errors');
export const couponIssueDuration = new Trend('coupon_issue_duration');
export const orderFlowDuration = new Trend('order_flow_duration');

/**
 * 랜덤 사용자 ID 생성 (1 ~ maxUserId)
 */
export function getRandomUserId(maxUserId = 1000) {
    return Math.floor(Math.random() * maxUserId) + 1;
}

/**
 * 랜덤 상품 ID 선택
 */
export function getRandomProductId() {
    return TEST_PRODUCT_IDS[Math.floor(Math.random() * TEST_PRODUCT_IDS.length)];
}

/**
 * 랜덤 수량 생성 (1 ~ maxQuantity)
 */
export function getRandomQuantity(maxQuantity = 3) {
    return Math.floor(Math.random() * maxQuantity) + 1;
}

/**
 * 상품 조회 API 호출
 */
export function getProduct(productId, userId) {
    const url = `${BASE_URL}/products/${productId}`;
    const response = http.get(url, {
        headers: getAuthHeaders(userId),
        tags: { name: 'GET_Product' },
    });

    const success = check(response, {
        'product status is 200': (r) => r.status === 200,
        'product has data': (r) => r.json('data') !== null,
    });

    if (!success) {
        errorRate.add(1);
    }

    return response;
}

/**
 * 장바구니에 상품 추가 API 호출
 */
export function addToCart(userId, productId, quantity) {
    const url = `${BASE_URL}/carts/items`;
    const payload = JSON.stringify({
        productId: productId,
        quantity: quantity,
    });

    const response = http.post(url, payload, {
        headers: getAuthHeaders(userId),
        tags: { name: 'POST_AddToCart' },
    });

    const success = check(response, {
        'cart add status is 201': (r) => r.status === 201,
        'cart item created': (r) => r.json('data.cartItemId') !== undefined,
    });

    if (!success) {
        errorRate.add(1);
    }

    return response;
}

/**
 * 주문 생성 API 호출
 */
export function createOrder(userId, cartItemIds) {
    const url = `${BASE_URL}/orders`;
    const payload = JSON.stringify({
        cartItemIds: cartItemIds,
        shippingAddress: {
            recipientName: `테스트사용자${userId}`,
            phone: '010-1234-5678',
            zipCode: '12345',
            address: '서울시 강남구 테헤란로 123',
            detailAddress: `${userId}호`,
        },
    });

    const response = http.post(url, payload, {
        headers: getAuthHeaders(userId),
        tags: { name: 'POST_CreateOrder' },
    });

    const success = check(response, {
        'order status is 201': (r) => r.status === 201,
        'order created': (r) => r.json('data.orderId') !== undefined,
    });

    if (success) {
        orderCreatedCounter.add(1);
    } else {
        errorRate.add(1);
    }

    return response;
}

/**
 * 결제 처리 API 호출
 */
export function processPayment(userId, orderId, couponIds = []) {
    const url = `${BASE_URL}/orders/${orderId}/payment`;
    const payload = JSON.stringify({
        paymentMethod: 'POINT',
        couponIds: couponIds,
    });

    const response = http.post(url, payload, {
        headers: getAuthHeaders(userId),
        tags: { name: 'POST_Payment' },
    });

    const success = check(response, {
        'payment status is 200': (r) => r.status === 200,
        'payment completed': (r) => r.json('data.status') === 'COMPLETED',
    });

    if (success) {
        paymentSuccessCounter.add(1);
    } else {
        paymentFailedCounter.add(1);
        errorRate.add(1);
    }

    return response;
}

/**
 * 잔액 충전 API 호출
 */
export function chargeBalance(userId, amount) {
    const url = `${BASE_URL}/balance/charge`;
    const payload = JSON.stringify({
        amount: amount,
    });

    const response = http.post(url, payload, {
        headers: getAuthHeaders(userId),
        tags: { name: 'POST_ChargeBalance' },
    });

    const success = check(response, {
        'charge status is 200': (r) => r.status === 200,
        'charge successful': (r) => r.json('data.transactionId') !== undefined,
    });

    if (!success) {
        errorRate.add(1);
    }

    return response;
}

/**
 * 쿠폰 발급 요청 API 호출 (비동기 - Kafka)
 */
export function requestCouponIssue(userId, couponId) {
    const url = `${BASE_URL}/coupons/${couponId}/request`;

    const startTime = new Date();
    const response = http.post(url, null, {
        headers: getAuthHeaders(userId),
        tags: { name: 'POST_CouponRequest' },
    });
    const duration = new Date() - startTime;
    couponIssueDuration.add(duration);

    const success = check(response, {
        'coupon request accepted': (r) => r.status === 202,
    });

    if (success) {
        couponIssuedCounter.add(1);
    } else {
        couponFailedCounter.add(1);
        errorRate.add(1);
    }

    return response;
}

/**
 * 쿠폰 발급 API 호출 (비동기 대기열 기반)
 */
export function issueCoupon(userId, couponId) {
    const url = `${BASE_URL}/coupons/${couponId}/request`;

    const startTime = new Date();
    const response = http.post(url, null, {
        headers: getAuthHeaders(userId),
        tags: { name: 'POST_CouponRequest' },
    });
    const duration = new Date() - startTime;
    couponIssueDuration.add(duration);

    // 202: 대기열 추가 성공, 409: 이미 발급됨/대기열에 있음, 400: 수량 소진
    const isSuccess = response.status === 202;
    const isAlreadyQueued = response.status === 409;
    const isSoldOut = response.status === 400;

    check(response, {
        'coupon issued or expected error': (r) => r.status === 202 || r.status === 409 || r.status === 400,
    });

    if (isSuccess) {
        couponIssuedCounter.add(1);
    } else if (isAlreadyQueued || isSoldOut) {
        // 이미 대기열에 있거나 소진된 경우는 예상된 결과
        couponFailedCounter.add(1);
    } else {
        errorRate.add(1);
        couponFailedCounter.add(1);
    }

    return response;
}

/**
 * 잔액 조회 API 호출
 */
export function getBalance(userId) {
    const url = `${BASE_URL}/balance`;

    const response = http.get(url, {
        headers: getAuthHeaders(userId),
        tags: { name: 'GET_Balance' },
    });

    check(response, {
        'balance status is 200': (r) => r.status === 200,
    });

    return response;
}

/**
 * 장바구니 조회 API 호출
 */
export function getCart(userId) {
    const url = `${BASE_URL}/carts`;

    const response = http.get(url, {
        headers: getAuthHeaders(userId),
        tags: { name: 'GET_Cart' },
    });

    check(response, {
        'cart status is 200': (r) => r.status === 200,
    });

    return response;
}

/**
 * Think time (실제 사용자 행동 시뮬레이션)
 */
export function thinkTime(minSeconds = 1, maxSeconds = 3) {
    const duration = Math.random() * (maxSeconds - minSeconds) + minSeconds;
    sleep(duration);
}

/**
 * 짧은 대기 (API 호출 사이)
 */
export function shortWait() {
    sleep(0.1 + Math.random() * 0.2);
}
