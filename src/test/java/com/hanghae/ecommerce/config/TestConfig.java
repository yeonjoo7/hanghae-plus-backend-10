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
@org.springframework.context.annotation.Import(EmbeddedRedisConfig.class)
public class TestConfig {
  // LockManager bean is removed to use the @Primary RedissonLockManager
}
