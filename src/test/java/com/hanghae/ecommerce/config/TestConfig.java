package com.hanghae.ecommerce.config;

import com.hanghae.ecommerce.infrastructure.lock.LockManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for providing test-specific beans
 */
@TestConfiguration
@Profile("test")
public class TestConfig {

  @Bean
  @Primary
  public LockManager lockManager() {
    return new TestLockManager();
  }

  /**
   * Test implementation of LockManager that always succeeds
   */
  public static class TestLockManager implements LockManager {
    @Override
    public boolean tryLock(String lockKey) {
      return true; // Always succeed in tests
    }

    @Override
    public boolean tryLock(String lockKey, long timeout, java.util.concurrent.TimeUnit timeUnit) {
      return true; // Always succeed in tests
    }

    @Override
    public void unlock(String lockKey) {
      // No-op in tests
    }

    @Override
    public <T> T executeWithLock(String lockKey, LockTask<T> task) {
      try {
        return task.execute();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public <T> T executeWithLock(String lockKey, long timeout, java.util.concurrent.TimeUnit timeUnit,
        LockTask<T> task) {
      try {
        return task.execute();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public int getActiveLockCount() {
      return 0; // Always 0 in tests
    }

    @Override
    public void clearAllLocks() {
      // No-op in tests
    }
  }
}
