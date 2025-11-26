package com.hanghae.ecommerce.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 * 
 * RedisTemplate을 구성하여 JSON 직렬화를 지원하고,
 * 인기 상품 캐싱에 필요한 Redis 연결을 관리합니다.
 */
@Configuration
public class RedisConfig {

  /**
   * RedisTemplate 빈 설정
   * 
   * Key는 String으로, Value는 JSON으로 직렬화하여 저장합니다.
   * 
   * @param connectionFactory Redis 연결 팩토리 (Spring Boot 자동 설정)
   * @return 설정된 RedisTemplate
   */
  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // ObjectMapper 설정
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // GenericJackson2JsonRedisSerializer 사용 (타입 정보 자동 포함)
    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

    // Key는 String으로 직렬화
    StringRedisSerializer stringSerializer = new StringRedisSerializer();

    template.setKeySerializer(stringSerializer);
    template.setValueSerializer(serializer);
    template.setHashKeySerializer(stringSerializer);
    template.setHashValueSerializer(serializer);

    template.afterPropertiesSet();
    return template;
  }
}
