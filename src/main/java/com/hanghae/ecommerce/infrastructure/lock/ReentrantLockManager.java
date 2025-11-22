package com.hanghae.ecommerce.infrastructure.lock;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ReentrantLock 기반의 인메모리 락 매니저 구현체
 * 단일 인스턴스 환경에서 동시성 제어를 제공합니다.
 */
@Component
public class ReentrantLockManager implements LockManager {

  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  private ReentrantLock getLock(String lockKey) {
    return locks.computeIfAbsent(lockKey, k -> new ReentrantLock(true)); // Fair lock
  }

  @Override
  public boolean tryLock(String lockKey) throws InterruptedException {
    return getLock(lockKey).tryLock();
  }

  @Override
  public boolean tryLock(String lockKey, long timeout, TimeUnit timeUnit) throws InterruptedException {
    return getLock(lockKey).tryLock(timeout, timeUnit);
  }

  @Override
  public void unlock(String lockKey) {
    ReentrantLock lock = locks.get(lockKey);
    if (lock != null && lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
  }

  @Override
  public <T> T executeWithLock(String lockKey, LockTask<T> task) {
    ReentrantLock lock = getLock(lockKey);
    lock.lock();
    try {
      return task.execute();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public <T> T executeWithLock(String lockKey, long timeout, TimeUnit timeUnit, LockTask<T> task) {
    ReentrantLock lock = getLock(lockKey);
    try {
      if (!lock.tryLock(timeout, timeUnit)) {
        throw new RuntimeException("Failed to acquire lock: " + lockKey);
      }
      try {
        return task.execute();
      } finally {
        lock.unlock();
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
    return (int) locks.values().stream().filter(ReentrantLock::isLocked).count();
  }

  @Override
  public void clearAllLocks() {
    locks.clear();
  }
}
