package com.hanghae.ecommerce.infrastructure.external;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@RestController
@RequestMapping("/api")
@Profile("mock-server")
public class MockDataPlatformServer {

    private final Map<String, List<Map<String, Object>>> receivedData = new ConcurrentHashMap<>();
    private final Random random = new Random();

    /**
     * 주문 데이터 수신 API (간헐적 실패 시뮬레이션 포함)
     */
    @PostMapping("/orders")
    public Map<String, Object> receiveOrder(@RequestBody Map<String, Object> body) {
        // 간헐적 실패 (20% 확률)
        if (random.nextDouble() < 0.2) {
            throw new RuntimeException("Internal Server Error (Mock Failure)");
        }

        // 성공 시 데이터 저장
        receivedData.computeIfAbsent("orders", k -> Collections.synchronizedList(new ArrayList<>()))
                .add(body);

        return Map.of(
                "success", true,
                "id", System.currentTimeMillis(),
                "message", "Order data received successfully"
        );
    }

    /**
     * 테스트용 - 수신된 주문 전체 조회
     */
    @GetMapping("/orders")
    public Map<String, Object> getOrders() {
        List<Map<String, Object>> orders = receivedData.getOrDefault("orders", new ArrayList<>());
        return Map.of(
                "total", orders.size(),
                "orders", orders
        );
    }

    /**
     * 테스트용 - 수신된 데이터 초기화
     */
    @DeleteMapping("/orders")
    public Map<String, Object> clearOrders() {
        receivedData.clear();
        return Map.of(
                "success", true,
                "message", "All data cleared"
        );
    }

    /**
     * 헬스체크 엔드포인트
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        );
    }

    public static void main(String[] args) {
        System.setProperty("server.port", "4000");
        SpringApplication.run(MockDataPlatformServer.class, args);
    }
}