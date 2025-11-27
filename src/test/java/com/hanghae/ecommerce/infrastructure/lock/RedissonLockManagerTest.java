package com.hanghae.ecommerce.infrastructure.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedissonLockManagerTest {

  @Mock
  private RedissonClient redissonClient;

  @Mock
  private RLock rLock;

  private RedissonLockManager redissonLockManager;

  @BeforeEach
  void setUp() {
    redissonLockManager = new RedissonLockManager(redissonClient);
  }

  @Test
  @DisplayName("락 획득 성공 테스트")
  void tryLock_Success() throws InterruptedException {
    // given
    String lockKey = "test-lock";
    given(redissonClient.getLock(lockKey)).willReturn(rLock);
    given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);

    // when
    boolean result = redissonLockManager.tryLock(lockKey);

    // then
    assertThat(result).isTrue();
    verify(redissonClient).getLock(lockKey);
    // 기본값: waitTime=5, leaseTime=10
    verify(rLock).tryLock(5L, 10L, TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("락 획득 실패 테스트")
  void tryLock_Failure() throws InterruptedException {
    // given
    String lockKey = "test-lock";
    given(redissonClient.getLock(lockKey)).willReturn(rLock);
    given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

    // when
    boolean result = redissonLockManager.tryLock(lockKey);

    // then
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("락 해제 테스트 - 현재 스레드가 락을 보유한 경우")
  void unlock_HeldByCurrentThread() {
    // given
    String lockKey = "test-lock";
    given(redissonClient.getLock(lockKey)).willReturn(rLock);
    given(rLock.isHeldByCurrentThread()).willReturn(true);

    // when
    redissonLockManager.unlock(lockKey);

    // then
    verify(rLock).unlock();
  }

  @Test
  @DisplayName("락 해제 테스트 - 현재 스레드가 락을 보유하지 않은 경우")
  void unlock_NotHeldByCurrentThread() {
    // given
    String lockKey = "test-lock";
    given(redissonClient.getLock(lockKey)).willReturn(rLock);
    given(rLock.isHeldByCurrentThread()).willReturn(false);

    // when
    redissonLockManager.unlock(lockKey);

    // then
    verify(rLock, never()).unlock();
  }

  @Test
  @DisplayName("executeWithLock 성공 테스트")
  void executeWithLock_Success() throws Exception {
    // given
    String lockKey = "test-lock";
    given(redissonClient.getLock(lockKey)).willReturn(rLock);
    given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
    given(rLock.isHeldByCurrentThread()).willReturn(true);

    // when
    String result = redissonLockManager.executeWithLock(lockKey, () -> "success");

    // then
    assertThat(result).isEqualTo("success");
    verify(rLock).tryLock(5L, 10L, TimeUnit.SECONDS);
    verify(rLock).unlock();
  }
}
