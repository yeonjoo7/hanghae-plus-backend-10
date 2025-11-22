package com.hanghae.ecommerce.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Base64;

/**
 * 테스트 환경용 인증 인터셉터
 * Authorization 헤더에서 사용자 ID를 추출하여 요청 속성에 저장합니다.
 */
@Component
@Profile("test")
public class TestAuthInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String authHeader = request.getHeader("Authorization");

    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);

      try {
        // JWT 토큰에서 payload 추출 (header.payload.signature 형식)
        String[] parts = token.split("\\.");
        if (parts.length >= 2) {
          String payload = new String(Base64.getDecoder().decode(parts[1]));

          // {"userId":123,"email":"..."} 형식에서 userId 추출
          String userIdStr = extractUserId(payload);
          if (userIdStr != null) {
            request.setAttribute("CURRENT_USER_ID", Long.parseLong(userIdStr));
          }
        }
      } catch (Exception e) {
        // 토큰 파싱 실패 시 무시 (기본값 사용)
      }
    }

    return true;
  }

  private String extractUserId(String payload) {
    // 간단한 JSON 파싱: "userId":123 형식에서 숫자 추출
    int userIdIndex = payload.indexOf("\"userId\":");
    if (userIdIndex != -1) {
      int start = userIdIndex + 9; // "userId": 다음부터
      int end = payload.indexOf(",", start);
      if (end == -1) {
        end = payload.indexOf("}", start);
      }
      if (end != -1) {
        return payload.substring(start, end).trim();
      }
    }
    return null;
  }
}
