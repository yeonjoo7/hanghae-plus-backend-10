package com.hanghae.ecommerce.infrastructure.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 기반의 분산 락 매니저 구현체
 * Redis를 사용하여 분산 환경에서의 동시성 제어를 제공합니다.
 */
@Component
@Primary
public class RedissonLockManager implements LockManager {

  private final RedissonClient redissonClient;

  // 락 획득 대기 시간 (기본값)
  private static final long DEFAULT_WAIT_TIME = 5L;
  // 락 점유 시간 (TTL) (기본값)
  private static final long DEFAULT_LEASE_TIME = 10L;
  private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

  public RedissonLockManager(RedissonClient redissonClient) {
    this.redissonClient = redissonClient;
  }

  @Override
  public boolean tryLock(String lockKey) throws InterruptedException {
    RLock lock = redissonClient.getLock(lockKey);
    return lock.tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, DEFAULT_TIME_UNIT);
  }

  @Override
  public boolean tryLock(String lockKey, long timeout, TimeUnit timeUnit) throws InterruptedException {
    RLock lock = redissonClient.getLock(lockKey);
    // timeout을 대기 시간으로 사용하고, leaseTime은 기본값 사용
    return lock.tryLock(timeout, DEFAULT_LEASE_TIME, timeUnit);
  }

  @Override
  public void unlock(String lockKey) {
    RLock lock = redissonClient.getLock(lockKey);
    if (lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
  }

  @Override
  public <T> T executeWithLock(String lockKey, LockTask<T> task) {
    return executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_TIME_UNIT, task);
  }

  @Override
  public <T> T executeWithLock(String lockKey, long timeout, TimeUnit timeUnit, LockTask<T> task) {
    RLock lock = redissonClient.getLock(lockKey);
    try {
      // leaseTime은 10초로 설정하여 락이 영원히 유지되는 것을 방지
      boolean available = lock.tryLock(timeout, DEFAULT_LEASE_TIME, timeUnit);

      if (!available) {
        throw new RuntimeException("Failed to acquire lock: " + lockKey);
      }

      try {
        return task.execute();
      } finally {
        if (lock.isHeldByCurrentThread()) {
          lock.unlock();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while acquiring lock: " + lockKey, e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getActiveLockCount() {
    // 분산 환경에서는 정확한 전체 락 개수를 파악하기 어려움
    return 0;
  }

  @Override
  public void clearAllLocks() {
    // 분산 환경에서는 모든 락을 해제하는 것이 위험할 수 있음
    // 필요한 경우 구현
  }
}
