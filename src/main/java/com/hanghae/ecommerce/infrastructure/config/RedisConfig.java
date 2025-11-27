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

    if (password != null && !password.isEmpty()) {
      config.useSingleServer()
          .setAddress(address)
          .setPassword(password);
    } else {
      config.useSingleServer()
          .setAddress(address);
    }

    return Redisson.create(config);
  }
}
