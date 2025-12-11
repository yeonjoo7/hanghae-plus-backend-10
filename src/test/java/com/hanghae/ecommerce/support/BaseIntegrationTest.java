package com.hanghae.ecommerce.support;

import com.hanghae.ecommerce.config.TestConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * 통합 테스트 베이스 클래스
 *
 * Testcontainers를 사용하여 Redis 컨테이너를 자동으로 시작하고,
 * 독립적인 테스트 환경을 보장합니다.
 *
 * Singleton 패턴을 사용하여 모든 테스트 클래스가 동일한 Redis 컨테이너를 공유합니다.
 * 이는 Spring 컨텍스트 캐싱과 호환되며, 테스트 실행 속도를 향상시킵니다.
 *
 * Docker가 실행 중이어야 합니다. Docker가 없으면 테스트가 실패합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

  /**
   * Singleton Redis 컨테이너
   * 모든 테스트 클래스에서 동일한 컨테이너를 공유합니다.
   * @Container 어노테이션 대신 static 블록에서 수동으로 시작합니다.
   */
  private static final GenericContainer<?> REDIS_CONTAINER;

  static {
    REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
    REDIS_CONTAINER.start();
  }

  /**
   * 동적 프로퍼티 설정
   * Testcontainers가 할당한 동적 포트를 Spring 설정에 주입합니다.
   */
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", REDIS_CONTAINER::getFirstMappedPort);
  }
}
