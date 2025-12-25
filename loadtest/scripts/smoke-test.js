/**
 * Smoke Test - 기본 API 동작 검증
 *
 * 목적: 각 API 엔드포인트의 정상 동작 확인
 * VUs: 10
 * Duration: 1분
 *
 * 실행: k6 run loadtest/scripts/smoke-test.js
 */

import { sleep } from 'k6';
import { SMOKE_TEST_OPTIONS } from '../utils/config.js';
import {
    getRandomUserId,
    getRandomProductId,
    getProduct,
    addToCart,
    getCart,
    chargeBalance,
    getBalance,
    issueCoupon,
    shortWait,
} from '../utils/helpers.js';

export const options = SMOKE_TEST_OPTIONS;

export default function () {
    const userId = getRandomUserId(100); // 1~100 사용자
    const productId = getRandomProductId();

    // 1. 상품 조회
    getProduct(productId, userId);
    shortWait();

    // 2. 잔액 조회
    getBalance(userId);
    shortWait();

    // 3. 잔액 충전 (1만원 ~ 10만원)
    const chargeAmount = (Math.floor(Math.random() * 10) + 1) * 10000;
    chargeBalance(userId, chargeAmount);
    shortWait();

    // 4. 장바구니 조회
    getCart(userId);
    shortWait();

    // 5. 장바구니에 상품 추가
    const quantity = Math.floor(Math.random() * 2) + 1;
    addToCart(userId, productId, quantity);
    shortWait();

    // 6. 쿠폰 발급 시도 (couponId: 1)
    issueCoupon(userId, 1);
    shortWait();

    // 다음 반복까지 대기
    sleep(1);
}

export function handleSummary(data) {
    return {
        'loadtest/results/smoke-test-result.json': JSON.stringify(data, null, 2),
    };
}
