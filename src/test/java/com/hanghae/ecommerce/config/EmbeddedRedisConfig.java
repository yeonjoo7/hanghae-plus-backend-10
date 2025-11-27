package com.hanghae.ecommerce.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;

@TestConfiguration
@Profile("test")
public class EmbeddedRedisConfig {

  @Value("${spring.data.redis.port:6379}")
  private int redisPort;

  private RedisServer redisServer;

  @PostConstruct
  public void startRedis() throws IOException {
    try {
      redisServer = new RedisServer(redisPort);
      redisServer.start();
    } catch (Exception e) {
      // 이미 실행 중인 경우 무시 (다른 테스트에서 실행했을 수 있음)
      e.printStackTrace();
    }
  }

  @PreDestroy
  public void stopRedis() throws IOException {
    if (redisServer != null) {
      redisServer.stop();
    }
  }
}
