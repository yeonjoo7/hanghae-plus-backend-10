/**
 * Order Flow Load Test - 주문/결제 플로우 부하 테스트
 *
 * 목적: E2E 구매 프로세스의 처리량 및 안정성 검증
 * - 상품 조회 → 장바구니 추가 → 주문 생성 → 결제 처리
 * - 재고 동시성 제어 검증
 * - 트랜잭션 성능 측정
 *
 * 시나리오:
 * 1. 1분 동안 100 VUs로 램프업
 * 2. 2분 동안 500 VUs 유지 (중간 부하)
 * 3. 2분 동안 1000 VUs 유지 (피크 부하)
 * 4. 1분 동안 램프다운
 *
 * 실행: k6 run loadtest/scripts/order-flow-test.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { LOAD_TEST_OPTIONS, BASE_URL, getAuthHeaders, TEST_PRODUCT_IDS } from '../utils/config.js';

// 커스텀 메트릭
const ordersCreated = new Counter('orders_created');
const paymentSuccess = new Counter('payment_success');
const paymentFailed = new Counter('payment_failed');
const cartAdded = new Counter('cart_items_added');
const balanceCharged = new Counter('balance_charged');
const orderFlowDuration = new Trend('order_flow_duration');
const errorRate = new Rate('error_rate');

export const options = {
    scenarios: {
        order_flow: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },   // 램프업
                { duration: '1m', target: 100 },   // 중간 부하
                { duration: '1m', target: 200 },   // 피크 부하
                { duration: '30s', target: 0 },    // 램프다운
            ],
            gracefulRampDown: '15s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],           // 실패율 5% 미만
        http_req_duration: ['p(95)<1000'],        // 95% 요청 1000ms 이내
        order_flow_duration: ['p(95)<5000'],      // 전체 플로우 95% 5초 이내
        error_rate: ['rate<0.10'],                // 에러율 10% 미만
    },
};

// 랜덤 상품 선택
function getRandomProductId() {
    return TEST_PRODUCT_IDS[Math.floor(Math.random() * TEST_PRODUCT_IDS.length)];
}

// 랜덤 수량 (1~3)
function getRandomQuantity() {
    return Math.floor(Math.random() * 3) + 1;
}

export function setup() {
    console.log('='.repeat(60));
    console.log('Order Flow Load Test 시작');
    console.log(`Base URL: ${BASE_URL}`);
    console.log('='.repeat(60));

    return {
        startTime: new Date().toISOString(),
    };
}

export default function (data) {
    const userId = __VU; // VU ID를 사용자 ID로 사용 (1~200)
    const flowStartTime = new Date();

    group('Complete Order Flow', function () {
        // Step 1: 잔액 충전
        group('1. Charge Balance', function () {
            const chargeAmount = (Math.floor(Math.random() * 30) + 20) * 100000; // 200만 ~ 500만원
            const chargeUrl = `${BASE_URL}/balance/charge`;
            const chargePayload = JSON.stringify({ amount: chargeAmount });

            const chargeResponse = http.post(chargeUrl, chargePayload, {
                headers: getAuthHeaders(userId),
                tags: { name: 'POST_ChargeBalance' },
            });

            const chargeSuccess = check(chargeResponse, {
                'charge status 200': (r) => r.status === 200,
            });

            if (chargeSuccess) {
                balanceCharged.add(1);
            } else {
                errorRate.add(1);
                console.error(`[User ${userId}] Charge failed: ${chargeResponse.status}`);
                return; // 충전 실패시 플로우 중단
            }

            sleep(0.1);
        });

        // Step 2: 상품 조회
        let productId;
        group('2. Get Product', function () {
            productId = getRandomProductId();
            const productUrl = `${BASE_URL}/products/${productId}`;

            const productResponse = http.get(productUrl, {
                headers: getAuthHeaders(userId),
                tags: { name: 'GET_Product' },
            });

            check(productResponse, {
                'product status 200': (r) => r.status === 200,
            });

            sleep(0.1);
        });

        // Step 3: 장바구니에 상품 추가
        let cartItemId;
        group('3. Add to Cart', function () {
            const quantity = getRandomQuantity();
            const cartUrl = `${BASE_URL}/carts/items`;
            const cartPayload = JSON.stringify({
                productId: productId,
                quantity: quantity,
            });

            const cartResponse = http.post(cartUrl, cartPayload, {
                headers: getAuthHeaders(userId),
                tags: { name: 'POST_AddToCart' },
            });

            const cartSuccess = check(cartResponse, {
                'cart add status 201': (r) => r.status === 201,
            });

            if (cartSuccess) {
                cartAdded.add(1);
                try {
                    const cartData = JSON.parse(cartResponse.body);
                    cartItemId = cartData.data?.cartItemId;
                } catch (e) {
                    console.error(`[User ${userId}] Failed to parse cart response`);
                }
            } else {
                errorRate.add(1);
                console.error(`[User ${userId}] Add to cart failed: ${cartResponse.status} - ${cartResponse.body}`);
                return;
            }

            sleep(0.1);
        });

        if (!cartItemId) {
            console.error(`[User ${userId}] No cart item ID, skipping order`);
            return;
        }

        // Step 4: 주문 생성
        let orderId;
        group('4. Create Order', function () {
            const orderUrl = `${BASE_URL}/orders`;
            const orderPayload = JSON.stringify({
                cartItemIds: [cartItemId],
                shippingAddress: {
                    recipientName: `테스트사용자${userId}`,
                    phone: '010-1234-5678',
                    zipCode: '12345',
                    address: '서울시 강남구 테헤란로 123',
                    detailAddress: `${userId}호`,
                },
            });

            const orderResponse = http.post(orderUrl, orderPayload, {
                headers: getAuthHeaders(userId),
                tags: { name: 'POST_CreateOrder' },
            });

            const orderSuccess = check(orderResponse, {
                'order status 201': (r) => r.status === 201,
            });

            if (orderSuccess) {
                ordersCreated.add(1);
                try {
                    const orderData = JSON.parse(orderResponse.body);
                    orderId = orderData.data?.orderId;
                } catch (e) {
                    console.error(`[User ${userId}] Failed to parse order response`);
                }
            } else {
                errorRate.add(1);
                console.error(`[User ${userId}] Create order failed: ${orderResponse.status} - ${orderResponse.body}`);
                return;
            }

            sleep(0.1);
        });

        if (!orderId) {
            console.error(`[User ${userId}] No order ID, skipping payment`);
            return;
        }

        // Step 5: 결제 처리
        group('5. Process Payment', function () {
            const paymentUrl = `${BASE_URL}/orders/${orderId}/payment`;
            const paymentPayload = JSON.stringify({
                paymentMethod: 'POINT',
                couponIds: [],
            });

            const paymentResponse = http.post(paymentUrl, paymentPayload, {
                headers: getAuthHeaders(userId),
                tags: { name: 'POST_Payment' },
            });

            const paymentSuccessCheck = check(paymentResponse, {
                'payment status 200': (r) => r.status === 200,
            });

            if (paymentSuccessCheck) {
                paymentSuccess.add(1);
            } else {
                paymentFailed.add(1);
                errorRate.add(1);
                console.error(`[User ${userId}] Payment failed: ${paymentResponse.status} - ${paymentResponse.body}`);
            }
        });
    });

    // 전체 플로우 소요 시간 기록
    const flowDuration = new Date() - flowStartTime;
    orderFlowDuration.add(flowDuration);

    // 다음 반복 전 대기 (Think time)
    sleep(Math.random() * 2 + 1);
}

export function teardown(data) {
    console.log('\n' + '='.repeat(60));
    console.log('Order Flow Load Test 완료');
    console.log(`시작 시간: ${data.startTime}`);
    console.log(`종료 시간: ${new Date().toISOString()}`);
    console.log('='.repeat(60));
}

export function handleSummary(data) {
    const summary = generateSummary(data);

    return {
        'stdout': summary,
        'loadtest/results/order-flow-test-result.json': JSON.stringify(data, null, 2),
    };
}

function generateSummary(data) {
    let output = '\n';
    output += '='.repeat(70) + '\n';
    output += '  ORDER FLOW LOAD TEST RESULT\n';
    output += '='.repeat(70) + '\n\n';

    // 주문/결제 결과
    output += '[ 주문/결제 결과 ]\n';
    output += '-'.repeat(40) + '\n';

    const orders = data.metrics.orders_created?.values?.count || 0;
    const payments = data.metrics.payment_success?.values?.count || 0;
    const paymentsFailed = data.metrics.payment_failed?.values?.count || 0;
    const carts = data.metrics.cart_items_added?.values?.count || 0;
    const charges = data.metrics.balance_charged?.values?.count || 0;

    output += `  잔액 충전: ${charges}건\n`;
    output += `  장바구니 추가: ${carts}건\n`;
    output += `  주문 생성: ${orders}건\n`;
    output += `  결제 성공: ${payments}건\n`;
    output += `  결제 실패: ${paymentsFailed}건\n`;

    if (orders > 0) {
        const conversionRate = ((payments / orders) * 100).toFixed(2);
        output += `  전환율: ${conversionRate}%\n`;
    }

    output += '\n';

    // 성능 메트릭
    output += '[ 성능 메트릭 ]\n';
    output += '-'.repeat(40) + '\n';

    if (data.metrics.http_reqs) {
        output += `  총 HTTP 요청: ${data.metrics.http_reqs.values.count}\n`;
        output += `  요청률: ${data.metrics.http_reqs.values.rate.toFixed(2)} req/s\n`;
    }

    if (data.metrics.http_req_duration) {
        output += `  응답시간 (avg): ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms\n`;
        output += `  응답시간 (p50): ${data.metrics.http_req_duration.values['p(50)'].toFixed(2)}ms\n`;
        output += `  응답시간 (p95): ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms\n`;
        output += `  응답시간 (p99): ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms\n`;
        output += `  응답시간 (max): ${data.metrics.http_req_duration.values.max.toFixed(2)}ms\n`;
    }

    if (data.metrics.order_flow_duration) {
        output += `\n  [전체 플로우]\n`;
        output += `  플로우 소요시간 (avg): ${data.metrics.order_flow_duration.values.avg.toFixed(2)}ms\n`;
        output += `  플로우 소요시간 (p95): ${data.metrics.order_flow_duration.values['p(95)'].toFixed(2)}ms\n`;
    }

    if (data.metrics.http_req_failed) {
        const failRate = (data.metrics.http_req_failed.values.rate * 100).toFixed(2);
        output += `\n  HTTP 실패율: ${failRate}%\n`;
    }

    if (data.metrics.error_rate) {
        const errRate = (data.metrics.error_rate.values.rate * 100).toFixed(2);
        output += `  비즈니스 에러율: ${errRate}%\n`;
    }

    output += '\n';

    // Thresholds 결과
    output += '[ Thresholds 결과 ]\n';
    output += '-'.repeat(40) + '\n';

    if (data.thresholds) {
        let allPassed = true;
        for (const [name, threshold] of Object.entries(data.thresholds)) {
            const status = threshold.ok ? 'PASS' : 'FAIL';
            const icon = threshold.ok ? '[O]' : '[X]';
            output += `  ${icon} ${name}: ${status}\n`;
            if (!threshold.ok) allPassed = false;
        }

        output += '\n';
        if (allPassed) {
            output += '  >>> 모든 성능 기준 충족!\n';
        } else {
            output += '  >>> 일부 성능 기준 미달 - 개선 필요\n';
        }
    }

    output += '\n' + '='.repeat(70) + '\n';

    return output;
}
