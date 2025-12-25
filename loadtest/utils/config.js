/**
 * k6 부하 테스트 설정 파일
 */

// 환경 변수에서 BASE_URL 가져오기 (기본값: localhost:8080)
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트용 쿠폰 ID (선착순 100개 제한 쿠폰)
export const TEST_COUPON_ID = __ENV.COUPON_ID || 1;

// 테스트용 상품 ID
export const TEST_PRODUCT_IDS = [1, 2, 3, 4, 5];

// 공통 HTTP 헤더
export const DEFAULT_HEADERS = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
};

// 사용자 인증 헤더 생성
export function getAuthHeaders(userId) {
    return {
        ...DEFAULT_HEADERS,
        'Authorization': `Bearer User-${userId}`,
    };
}

// 테스트 성공 기준 (Thresholds)
export const THRESHOLDS = {
    // HTTP 요청 실패율 1% 미만
    http_req_failed: ['rate<0.01'],
    // 95% 요청이 500ms 이내
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
};

// Smoke Test 옵션 (기본 검증용)
export const SMOKE_TEST_OPTIONS = {
    vus: 10,
    duration: '1m',
    thresholds: THRESHOLDS,
};

// Load Test 옵션 (주문/결제 플로우)
export const LOAD_TEST_OPTIONS = {
    stages: [
        { duration: '1m', target: 100 },   // 램프업
        { duration: '2m', target: 500 },   // 중간 부하
        { duration: '2m', target: 1000 },  // 피크 부하
        { duration: '1m', target: 0 },     // 램프다운
    ],
    thresholds: THRESHOLDS,
};

// Spike Test 옵션 (쿠폰 발급)
export const SPIKE_TEST_OPTIONS = {
    stages: [
        { duration: '10s', target: 1000 }, // 10초 동안 1000 VUs로 급증
        { duration: '30s', target: 1000 }, // 30초 유지
        { duration: '10s', target: 0 },    // 10초 동안 감소
    ],
    thresholds: {
        ...THRESHOLDS,
        // 쿠폰 발급 성공률 체크
        'checks': ['rate>0.95'],
    },
};

// Stress Test 옵션 (한계 테스트)
export const STRESS_TEST_OPTIONS = {
    stages: [
        { duration: '2m', target: 100 },
        { duration: '5m', target: 500 },
        { duration: '5m', target: 1000 },
        { duration: '5m', target: 1500 },
        { duration: '2m', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.05'], // 스트레스 테스트에서는 5% 허용
        http_req_duration: ['p(95)<1000'],
    },
};
