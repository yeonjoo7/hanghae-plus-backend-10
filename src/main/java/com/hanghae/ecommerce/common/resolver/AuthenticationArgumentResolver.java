package com.hanghae.ecommerce.common.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanghae.ecommerce.common.annotation.AuthenticatedUser;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Base64;

/**
 * Authorization 헤더에서 JWT 토큰을 파싱하여 사용자 ID를 추출하는 Resolver
 * 테스트 환경을 위해 간단한 JWT 파싱 로직을 구현합니다.
 */
@Component
public class AuthenticationArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedUser.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        String authorizationHeader = webRequest.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("유효하지 않은 인증 헤더입니다.");
        }

        String token = authorizationHeader.substring(7);

        // 간단한 토큰 형식 지원 (Bearer-User-{userId})
        if (token.startsWith("User-")) {
            return Long.parseLong(token.substring(5));
        }

        // JWT 토큰 파싱 (header.payload.signature)
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("유효하지 않은 JWT 토큰 형식입니다.");
            }

            String payload = new String(Base64.getDecoder().decode(parts[1]));
            JsonNode jsonNode = objectMapper.readTree(payload);

            if (jsonNode.has("userId")) {
                return jsonNode.get("userId").asLong();
            } else {
                throw new IllegalArgumentException("토큰에 userId가 없습니다.");
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("토큰 파싱 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
