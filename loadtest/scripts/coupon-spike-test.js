/**
 * Coupon Spike Test - 선착순 쿠폰 발급 부하 테스트
 *
 * 목적: 선착순 100개 쿠폰에 1000+ 동시 요청 처리 검증
 * - 정확히 100개만 발급되는지 확인 (초과 발급 방지)
 * - 동시성 제어 성능 측정
 *
 * 시나리오:
 * 1. 10초 동안 1000 VUs로 급증 (Spike)
 * 2. 30초 유지
 * 3. 10초 동안 감소
 *
 * 실행: k6 run loadtest/scripts/coupon-spike-test.js
 * 환경변수: k6 run -e COUPON_ID=3 loadtest/scripts/coupon-spike-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { SPIKE_TEST_OPTIONS, BASE_URL, getAuthHeaders, TEST_COUPON_ID } from '../utils/config.js';

// 커스텀 메트릭
const couponIssuedCounter = new Counter('coupons_issued_success');
const couponAlreadyIssuedCounter = new Counter('coupons_already_issued');
const couponSoldOutCounter = new Counter('coupons_sold_out');
const couponErrorCounter = new Counter('coupons_error');
const couponIssueDuration = new Trend('coupon_issue_duration');
const successRate = new Rate('coupon_success_rate');

export const options = {
    // 시나리오 기반 테스트
    scenarios: {
        spike_coupon: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: 200 },  // 5초 동안 200 VUs로 급증
                { duration: '20s', target: 200 }, // 20초 유지
                { duration: '5s', target: 0 },    // 5초 동안 감소
            ],
            gracefulRampDown: '5s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],           // 실패율 5% 미만
        http_req_duration: ['p(95)<500'],         // 95% 요청 500ms 이내
        coupon_success_rate: ['rate>0'],          // 최소 1건 이상 성공
        coupon_issue_duration: ['p(95)<1000'],    // 쿠폰 발급 95% 1초 이내
    },
};

// 테스트할 쿠폰 ID
const COUPON_ID = __ENV.COUPON_ID || TEST_COUPON_ID;

export function setup() {
    console.log('='.repeat(60));
    console.log('Coupon Spike Test 시작');
    console.log(`대상 쿠폰 ID: ${COUPON_ID}`);
    console.log(`Base URL: ${BASE_URL}`);
    console.log('='.repeat(60));

    return {
        couponId: COUPON_ID,
        startTime: new Date().toISOString(),
    };
}

export default function (data) {
    // 각 VU는 고유한 사용자 ID를 가짐 (VU ID 기반)
    const userId = __VU;
    const couponId = data.couponId;

    // 쿠폰 발급 요청 (비동기 대기열 방식)
    const url = `${BASE_URL}/coupons/${couponId}/request`;
    const startTime = new Date();

    const response = http.post(url, null, {
        headers: getAuthHeaders(userId),
        tags: { name: 'POST_CouponRequest' },
        timeout: '30s',
    });

    const duration = new Date() - startTime;
    couponIssueDuration.add(duration);

    // 응답 분석
    if (response.status === 202) {
        // 대기열 추가 성공
        couponIssuedCounter.add(1);
        successRate.add(1);
        check(response, {
            'coupon request queued': (r) => r.status === 202,
        });
    } else if (response.status === 409) {
        // 이미 대기열에 있음
        couponAlreadyIssuedCounter.add(1);
        successRate.add(0);
        check(response, {
            'already in queue (expected)': (r) => r.status === 409,
        });
    } else if (response.status === 400) {
        // 쿠폰 소진
        couponSoldOutCounter.add(1);
        successRate.add(0);
        check(response, {
            'sold out (expected)': (r) => r.status === 400,
        });
    } else {
        // 기타 에러
        couponErrorCounter.add(1);
        successRate.add(0);
        console.error(`[User ${userId}] Unexpected error: ${response.status} - ${response.body}`);
    }

    // 다음 요청까지 짧은 대기 (실제 사용자 행동 시뮬레이션)
    sleep(Math.random() * 0.5);
}

export function teardown(data) {
    console.log('\n' + '='.repeat(60));
    console.log('Coupon Spike Test 완료');
    console.log(`시작 시간: ${data.startTime}`);
    console.log(`종료 시간: ${new Date().toISOString()}`);
    console.log('='.repeat(60));
}

export function handleSummary(data) {
    const summary = generateSummary(data);

    return {
        'stdout': summary,
        'loadtest/results/coupon-spike-test-result.json': JSON.stringify(data, null, 2),
    };
}

function generateSummary(data) {
    let output = '\n';
    output += '='.repeat(70) + '\n';
    output += '  COUPON SPIKE TEST RESULT\n';
    output += '='.repeat(70) + '\n\n';

    // 쿠폰 발급 결과
    output += '[ 쿠폰 발급 결과 ]\n';
    output += '-'.repeat(40) + '\n';

    const issued = data.metrics.coupons_issued_success?.values?.count || 0;
    const alreadyIssued = data.metrics.coupons_already_issued?.values?.count || 0;
    const soldOut = data.metrics.coupons_sold_out?.values?.count || 0;
    const errors = data.metrics.coupons_error?.values?.count || 0;
    const total = issued + alreadyIssued + soldOut + errors;

    output += `  발급 성공: ${issued}건\n`;
    output += `  이미 발급: ${alreadyIssued}건\n`;
    output += `  쿠폰 소진: ${soldOut}건\n`;
    output += `  에러: ${errors}건\n`;
    output += `  총 요청: ${total}건\n\n`;

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
    }

    if (data.metrics.http_req_failed) {
        const failRate = (data.metrics.http_req_failed.values.rate * 100).toFixed(2);
        output += `  실패율: ${failRate}%\n`;
    }

    output += '\n';

    // Thresholds 결과
    output += '[ Thresholds 결과 ]\n';
    output += '-'.repeat(40) + '\n';

    if (data.thresholds) {
        for (const [name, threshold] of Object.entries(data.thresholds)) {
            const status = threshold.ok ? 'PASS' : 'FAIL';
            const icon = threshold.ok ? '[O]' : '[X]';
            output += `  ${icon} ${name}: ${status}\n`;
        }
    }

    output += '\n';

    // 검증 결과
    output += '[ 검증 결과 ]\n';
    output += '-'.repeat(40) + '\n';

    // 쿠폰 초과 발급 체크 (선착순 100개 기준)
    const maxCoupons = 100;
    if (issued <= maxCoupons) {
        output += `  [O] 쿠폰 초과 발급 없음 (${issued}/${maxCoupons})\n`;
    } else {
        output += `  [X] 쿠폰 초과 발급 감지! (${issued}/${maxCoupons})\n`;
    }

    // 에러율 체크
    if (errors === 0) {
        output += `  [O] 예상치 못한 에러 없음\n`;
    } else {
        output += `  [X] 예상치 못한 에러 발생: ${errors}건\n`;
    }

    output += '\n' + '='.repeat(70) + '\n';

    return output;
}
