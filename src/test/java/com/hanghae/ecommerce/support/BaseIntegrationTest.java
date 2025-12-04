package com.hanghae.ecommerce.support;

import com.hanghae.ecommerce.config.TestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 베이스 클래스
 * 
 * Testcontainers를 사용하여 Redis 컨테이너를 자동으로 시작하고,
 * 독립적인 테스트 환경을 보장합니다.
 * 
 * @DynamicPropertySource를 통해 Testcontainers가 할당한 동적 포트가
 *                         자동으로 Spring 설정에 주입됩니다.
 * 
 *                         Docker가 실행 중이지 않은 경우, application-test.yml의 기본 설정을
 *                         사용합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {

  @Container
  private static final GenericContainer<?> redisContainer = new GenericContainer<>(
      DockerImageName.parse("redis:7-alpine"))
      .withExposedPorts(6379)
      .withReuse(true);

  /**
   * 동적 프로퍼티 설정
   * Testcontainers가 할당한 동적 포트를 Spring 설정에 주입합니다.
   * 
   * Docker가 실행 중이어야 합니다. Docker가 없으면 테스트가 실패합니다.
   */
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redisContainer::getHost);
    registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
  }

  /**
   * 컨테이너가 완전히 시작될 때까지 대기
   * 
   * 모든 테스트가 시작되기 전에 Redis 컨테이너가 준비되었는지 확인합니다.
   */
  @BeforeAll
  static void waitForContainer() {
    if (!redisContainer.isRunning()) {
      redisContainer.start();
    }
    // 컨테이너가 완전히 시작될 때까지 대기
    // Testcontainers가 자동으로 대기하지만, 명시적으로 확인
    try {
      Thread.sleep(500); // 컨테이너 초기화 대기
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
