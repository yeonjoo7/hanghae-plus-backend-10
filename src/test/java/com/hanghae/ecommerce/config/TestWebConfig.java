package com.hanghae.ecommerce.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 테스트 환경용 Web MVC 설정
 */
@Configuration
@Profile("test")
public class TestWebConfig implements WebMvcConfigurer {

  @Autowired
  private TestAuthInterceptor testAuthInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(testAuthInterceptor)
        .addPathPatterns("/**");
  }
}
