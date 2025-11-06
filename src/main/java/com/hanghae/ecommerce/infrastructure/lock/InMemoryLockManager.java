package com.hanghae.ecommerce.infrastructure.lock;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 인메모리 기반 락 매니저 구현체
 * 
 * ReentrantLock과 ConcurrentHashMap을 사용하여 
 * JVM 내에서 동시성을 제어합니다.
 * 
 * 특징:
 * - 쿠폰별, 상품별로 독립적인 락 관리
 * - 공정성(fairness) 보장으로 FIFO 순서 처리
 * - 타임아웃 지원으로 무한 대기 방지
 * - 자동 락 정리로 메모리 누수 방지
 */
@Component
public class InMemoryLockManager implements LockManager {

    // 락 키별로 개별 락 인스턴스를 관리하는 맵
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    
    // 락 생성 시 동기화를 위한 내부 락
    private final ReentrantLock lockCreationLock = new ReentrantLock();

    @Override
    public boolean tryLock(String lockKey) throws InterruptedException {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("락 키는 null이거나 빈 문자열일 수 없습니다.");
        }

        ReentrantLock lock = getOrCreateLock(lockKey);
        return lock.tryLock();
    }

    @Override
    public boolean tryLock(String lockKey, long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("락 키는 null이거나 빈 문자열일 수 없습니다.");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("타임아웃은 0 이상이어야 합니다.");
        }
        if (timeUnit == null) {
            throw new IllegalArgumentException("시간 단위는 null일 수 없습니다.");
        }

        ReentrantLock lock = getOrCreateLock(lockKey);
        return lock.tryLock(timeout, timeUnit);
    }

    @Override
    public void unlock(String lockKey) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            return; // 무시
        }

        ReentrantLock lock = locks.get(lockKey);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            
            // 락이 더 이상 사용되지 않으면 정리
            cleanupLockIfUnused(lockKey, lock);
        }
    }

    @Override
    public <T> T executeWithLock(String lockKey, LockTask<T> task) {
        return executeWithLock(lockKey, 10, TimeUnit.SECONDS, task);
    }

    @Override
    public <T> T executeWithLock(String lockKey, long timeout, TimeUnit timeUnit, LockTask<T> task) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("락 키는 null이거나 빈 문자열일 수 없습니다.");
        }
        if (task == null) {
            throw new IllegalArgumentException("작업은 null일 수 없습니다.");
        }

        boolean lockAcquired = false;
        try {
            lockAcquired = tryLock(lockKey, timeout, timeUnit);
            if (!lockAcquired) {
                throw new RuntimeException("락 획득에 실패했습니다. 키: " + lockKey + ", 타임아웃: " + timeout + " " + timeUnit);
            }

            return task.execute();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("작업 실행 중 오류 발생", e);
        } finally {
            if (lockAcquired) {
                unlock(lockKey);
            }
        }
    }

    @Override
    public int getActiveLockCount() {
        return (int) locks.values().stream()
                .filter(lock -> lock.isLocked())
                .count();
    }

    @Override
    public void clearAllLocks() {
        locks.clear();
    }

    /**
     * 락 키에 해당하는 락을 가져오거나 새로 생성합니다.
     * 
     * @param lockKey 락 키
     * @return ReentrantLock 인스턴스
     */
    private ReentrantLock getOrCreateLock(String lockKey) {
        // 이미 존재하는 락이 있으면 반환
        ReentrantLock existingLock = locks.get(lockKey);
        if (existingLock != null) {
            return existingLock;
        }

        // 새 락 생성 시 동시성 제어
        lockCreationLock.lock();
        try {
            // Double-checked locking 패턴으로 중복 생성 방지
            existingLock = locks.get(lockKey);
            if (existingLock != null) {
                return existingLock;
            }

            // 공정성(fairness)을 true로 설정하여 FIFO 순서 보장
            // 선착순 쿠폰 발급에서 공정한 처리를 위함
            ReentrantLock newLock = new ReentrantLock(true);
            locks.put(lockKey, newLock);
            return newLock;
        } finally {
            lockCreationLock.unlock();
        }
    }

    /**
     * 사용하지 않는 락을 정리합니다.
     * 
     * @param lockKey 락 키
     * @param lock 락 인스턴스
     */
    private void cleanupLockIfUnused(String lockKey, ReentrantLock lock) {
        // 락이 사용 중이지 않고 대기 중인 스레드가 없으면 제거
        if (!lock.isLocked() && !lock.hasQueuedThreads()) {
            locks.remove(lockKey, lock);
        }
    }

    /**
     * 쿠폰 발급을 위한 락 키 생성 유틸리티 메서드
     * 
     * @param couponId 쿠폰 ID
     * @return 쿠폰 락 키
     */
    public static String createCouponLockKey(Long couponId) {
        return "coupon:" + couponId;
    }

    /**
     * 재고 관리를 위한 락 키 생성 유틸리티 메서드
     * 
     * @param productId 상품 ID
     * @return 상품 락 키
     */
    public static String createStockLockKey(Long productId) {
        return "stock:" + productId;
    }

    /**
     * 사용자 포인트 관리를 위한 락 키 생성 유틸리티 메서드
     * 
     * @param userId 사용자 ID
     * @return 사용자 락 키
     */
    public static String createUserLockKey(Long userId) {
        return "user:" + userId;
    }
}