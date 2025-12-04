package com.hanghae.ecommerce.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

  @Value("${spring.data.redis.host}")
  private String host;

  @Value("${spring.data.redis.port}")
  private int port;

  @Value("${spring.data.redis.password:}")
  private String password;

  @Bean
  public RedissonClient redissonClient() {
    Config config = new Config();
    String address = "redis://" + host + ":" + port;

    var singleServerConfig = config.useSingleServer()
        .setAddress(address)
        // 연결 timeout 설정 (30초)
        .setConnectTimeout(30000)
        // 명령 실행 timeout 설정 (15초)
        .setTimeout(15000)
        // 재시도 횟수 (3회)
        .setRetryAttempts(3)
        // 재시도 간격 (2초)
        .setRetryInterval(2000)
        // 연결 풀 설정 (동시성 테스트를 고려하여 증가)
        .setConnectionPoolSize(20)
        .setConnectionMinimumIdleSize(10);

    if (password != null && !password.isEmpty()) {
      singleServerConfig.setPassword(password);
    }

    return Redisson.create(config);
  }
}
