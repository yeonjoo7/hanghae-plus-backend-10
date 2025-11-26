package com.hanghae.ecommerce.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis Testcontainers 설정
 * 
 * 테스트 실행 시 Docker를 사용하여 Redis 컨테이너를 자동으로 시작하고,
 * 테스트 종료 시 자동으로 정리합니다.
 */
@TestConfiguration
public class RedisTestContainerConfig {

  private static RedisContainer REDIS_CONTAINER;

  /**
   * Redis 컨테이너 시작 (필요 시 호출)
   */
  public static void startRedisContainer() {
    if (REDIS_CONTAINER == null) {
      REDIS_CONTAINER = new RedisContainer(DockerImageName.parse("redis:7-alpine"))
          .withExposedPorts(6379)
          .withReuse(true); // 테스트 간 컨테이너 재사용으로 속도 향상

      REDIS_CONTAINER.start();
    }
  }

  /**
   * Redis 호스트 반환
   */
  public static String getRedisHost() {
    startRedisContainer();
    return REDIS_CONTAINER.getHost();
  }

  /**
   * Redis 포트 반환
   */
  public static Integer getRedisPort() {
    startRedisContainer();
    return REDIS_CONTAINER.getMappedPort(6379);
  }

  /**
   * Spring Boot의 Redis 설정을 Testcontainers의 Redis로 동적 설정
   */
  @DynamicPropertySource
  static void registerRedisProperties(DynamicPropertyRegistry registry) {
    startRedisContainer();
    registry.add("spring.data.redis.host", RedisTestContainerConfig::getRedisHost);
    registry.add("spring.data.redis.port", () -> getRedisPort().toString());
  }

  @Bean
  public RedisContainer redisContainer() {
    startRedisContainer();
    return REDIS_CONTAINER;
  }
}
