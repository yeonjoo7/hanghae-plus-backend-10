package com.hanghae.ecommerce.infrastructure.lock;

import java.util.concurrent.TimeUnit;

/**
 * 동시성 제어를 위한 락 매니저 인터페이스
 * 
 * 선착순 쿠폰 발급과 재고 관리 등에서 Race Condition을 방지하기 위한
 * 락 관리 기능을 제공합니다.
 */
public interface LockManager {

    /**
     * 지정된 키에 대한 락을 획득합니다.
     * 
     * @param lockKey 락 키 (쿠폰 ID, 상품 ID 등)
     * @return 락 획득 성공 여부
     * @throws InterruptedException 락 획득 중 인터럽트 발생
     */
    boolean tryLock(String lockKey) throws InterruptedException;

    /**
     * 지정된 키에 대한 락을 타임아웃과 함께 획득 시도합니다.
     * 
     * @param lockKey 락 키
     * @param timeout 타임아웃 시간
     * @param timeUnit 시간 단위
     * @return 락 획득 성공 여부
     * @throws InterruptedException 락 획득 중 인터럽트 발생
     */
    boolean tryLock(String lockKey, long timeout, TimeUnit timeUnit) throws InterruptedException;

    /**
     * 지정된 키에 대한 락을 해제합니다.
     * 
     * @param lockKey 락 키
     */
    void unlock(String lockKey);

    /**
     * 락과 함께 작업을 실행합니다.
     * 락 획득 → 작업 실행 → 락 해제를 자동으로 처리합니다.
     * 
     * @param lockKey 락 키
     * @param task 실행할 작업
     * @param <T> 작업 결과 타입
     * @return 작업 실행 결과
     * @throws RuntimeException 작업 실행 중 오류 발생
     */
    <T> T executeWithLock(String lockKey, LockTask<T> task);

    /**
     * 락과 함께 작업을 실행합니다. (타임아웃 지원)
     * 
     * @param lockKey 락 키
     * @param timeout 타임아웃 시간
     * @param timeUnit 시간 단위
     * @param task 실행할 작업
     * @param <T> 작업 결과 타입
     * @return 작업 실행 결과
     * @throws RuntimeException 작업 실행 중 오류 발생 또는 락 획득 실패
     */
    <T> T executeWithLock(String lockKey, long timeout, TimeUnit timeUnit, LockTask<T> task);

    /**
     * 현재 활성화된 락의 개수를 반환합니다.
     * 
     * @return 활성 락 개수
     */
    int getActiveLockCount();

    /**
     * 모든 락을 해제합니다. (주로 테스트나 종료 시 사용)
     */
    void clearAllLocks();

    /**
     * 락과 함께 실행할 작업을 정의하는 함수형 인터페이스
     * 
     * @param <T> 작업 결과 타입
     */
    @FunctionalInterface
    interface LockTask<T> {
        /**
         * 락이 획득된 상태에서 실행할 작업
         * 
         * @return 작업 결과
         * @throws Exception 작업 실행 중 발생할 수 있는 예외
         */
        T execute() throws Exception;
    }
}