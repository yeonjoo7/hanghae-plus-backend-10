package com.hanghae.ecommerce.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class RedisConfig {

  @Value("${spring.data.redis.host}")
  private String host;

  @Value("${spring.data.redis.port}")
  private int port;

  @Value("${spring.data.redis.password:}")
  private String password;

  private final Environment environment;

  public RedisConfig(Environment environment) {
    this.environment = environment;
  }

  @Bean
  public RedissonClient redissonClient() {
    Config config = new Config();
    String address = "redis://" + host + ":" + port;

    // 테스트 환경 여부 확인
    boolean isTestProfile = environment.matchesProfiles("test");

    // 테스트 환경에서는 timeout을 짧게 설정하여 빠르게 실패하도록 함
    int connectTimeout = isTestProfile ? 5000 : 30000; // 테스트: 5초, 운영: 30초
    int timeout = isTestProfile ? 3000 : 15000; // 테스트: 3초, 운영: 15초
    int retryAttempts = isTestProfile ? 1 : 3; // 테스트: 1회, 운영: 3회
    int retryInterval = isTestProfile ? 1000 : 2000; // 테스트: 1초, 운영: 2초

    var singleServerConfig = config.useSingleServer()
        .setAddress(address)
        .setConnectTimeout(connectTimeout)
        .setTimeout(timeout)
        .setRetryAttempts(retryAttempts)
        .setRetryInterval(retryInterval)
        // 연결 풀 설정 (동시성 테스트를 고려하여 증가)
        .setConnectionPoolSize(20)
        .setConnectionMinimumIdleSize(10);

    if (password != null && !password.isEmpty()) {
      singleServerConfig.setPassword(password);
    }

    return Redisson.create(config);
  }
}
