package com.hanghae.ecommerce.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 캐시 설정
 * 
 * 캐시 전략:
 * - Cache Aside 패턴: 캐시 미스 시 DB 조회 후 캐시에 저장
 * - TTL 기반 만료: 캐시 종류별 적절한 TTL 설정
 * - Cache Stampede 방지: 분산락을 통한 동시 캐시 갱신 방지
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    private final Environment environment;
    
    public RedisCacheConfig(Environment environment) {
        this.environment = environment;
    }

    // 캐시 이름 상수
    public static final String POPULAR_PRODUCTS_CACHE = "popularProducts";
    public static final String PRODUCT_CACHE = "product";
    public static final String PRODUCT_WITH_STOCK_CACHE = "productWithStock";
    public static final String PRODUCTS_CACHE = "products";

    // 캐시 TTL 설정
    private static final Duration POPULAR_PRODUCTS_TTL = Duration.ofMinutes(5);
    private static final Duration PRODUCT_TTL = Duration.ofMinutes(30);
    private static final Duration PRODUCT_WITH_STOCK_TTL = Duration.ofMinutes(5);
    private static final Duration PRODUCTS_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }

        // 테스트 환경 여부 확인
        boolean isTestProfile = environment.matchesProfiles("test");
        
        // Lettuce Client 설정: timeout 및 재연결 정책
        // 테스트 환경에서는 timeout을 짧게 설정하여 빠르게 실패하도록 함
        Duration connectTimeout = isTestProfile ? Duration.ofSeconds(5) : Duration.ofSeconds(30);
        Duration commandTimeout = isTestProfile ? Duration.ofSeconds(3) : Duration.ofSeconds(15);
        Duration shutdownTimeout = isTestProfile ? Duration.ofSeconds(2) : Duration.ofSeconds(5);
        
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(connectTimeout)  // 테스트: 5초, 운영: 30초
                .build();

        TimeoutOptions timeoutOptions = TimeoutOptions.builder()
                .fixedTimeout(commandTimeout)  // 테스트: 3초, 운영: 15초
                .build();

        // 테스트 환경에서는 자동 재연결 비활성화 (연결 실패 시 빠르게 실패하고 예외 처리로 폴백)
        // 운영 환경에서는 자동 재연결 활성화 (장애 복구를 위해 필요)
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .timeoutOptions(timeoutOptions)
                .autoReconnect(!isTestProfile)  // 테스트: false, 운영: true
                .build();
        
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(commandTimeout)  // 테스트: 3초, 운영: 15초
                .shutdownTimeout(shutdownTimeout)  // 테스트: 2초, 운영: 5초
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Key serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Value serializer (JSON)
        ObjectMapper objectMapper = createObjectMapper();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(createObjectMapper())))
                .disableCachingNullValues();

        // 캐시별 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // 인기 상품 캐시 (5분 TTL)
        cacheConfigurations.put(POPULAR_PRODUCTS_CACHE, defaultConfig.entryTtl(POPULAR_PRODUCTS_TTL));
        
        // 상품 캐시 (30분 TTL - 상품 정보는 자주 변경되지 않음)
        cacheConfigurations.put(PRODUCT_CACHE, defaultConfig.entryTtl(PRODUCT_TTL));
        
        // 상품+재고 캐시 (5분 TTL - 재고는 자주 변경됨)
        cacheConfigurations.put(PRODUCT_WITH_STOCK_CACHE, defaultConfig.entryTtl(PRODUCT_WITH_STOCK_TTL));
        
        // 상품 목록 캐시 (10분 TTL)
        cacheConfigurations.put(PRODUCTS_CACHE, defaultConfig.entryTtl(PRODUCTS_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return objectMapper;
    }
}

