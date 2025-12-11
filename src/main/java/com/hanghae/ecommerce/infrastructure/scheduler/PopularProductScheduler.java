package com.hanghae.ecommerce.infrastructure.scheduler;

import com.hanghae.ecommerce.application.product.PopularProductService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 인기 상품 관련 스케줄링 작업
 * 
 * 주기적으로 인기 상품 캐시를 갱신하고 오래된 데이터를 정리합니다.
 * 프로덕션 환경에서는 application.yml에서 활성화할 수 있습니다.
 */
@Component
@EnableScheduling
@Profile("!test")
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class PopularProductScheduler {

    private final PopularProductService popularProductService;

    public PopularProductScheduler(PopularProductService popularProductService) {
        this.popularProductService = popularProductService;
    }

    /**
     * 인기 상품 캐시 갱신 (5분마다 실행)
     * 
     * 자주 조회되는 인기 상품 데이터를 미리 계산하여
     * 사용자 요청 시 빠른 응답을 제공합니다.
     */
    @Scheduled(fixedRate = 300000) // 5분 = 300,000ms
    public void refreshPopularProductsCache() {
        try {
            popularProductService.refreshPopularProductsCache();
            System.out.println("인기 상품 캐시 갱신 완료: " + java.time.LocalDateTime.now());
        } catch (Exception e) {
            System.err.println("인기 상품 캐시 갱신 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 오래된 데이터 정리 (매일 새벽 2시 실행)
     * 
     * 30일 이상 된 판매 데이터를 삭제하여
     * 메모리 사용량을 관리합니다.
     */
    @Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시
    public void cleanupOldData() {
        try {
            popularProductService.cleanupOldData(30); // 30일 이상 된 데이터 삭제
            System.out.println("오래된 판매 데이터 정리 완료: " + java.time.LocalDateTime.now());
        } catch (Exception e) {
            System.err.println("데이터 정리 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 판매량 통계 리포트 (1시간마다 실행)
     * 
     * 판매량 집계 현황을 모니터링하기 위한 통계를 출력합니다.
     * 프로덕션에서는 로깅 시스템으로 전송하거나 메트릭으로 수집할 수 있습니다.
     */
    @Scheduled(fixedRate = 3600000) // 1시간 = 3,600,000ms
    public void reportSalesStats() {
        try {
            var stats = popularProductService.getSalesStats();
            
            System.out.printf(
                "[판매량 통계] 추적 일수: %d, 추적 상품수: %d, 총 판매량: %d%n",
                stats.getTotalDaysTracked(),
                stats.getTotalProductsTracked(),
                stats.getTotalSales()
            );
        } catch (Exception e) {
            System.err.println("판매량 통계 리포트 실패: " + e.getMessage());
        }
    }

    /**
     * 시스템 상태 체크 (10분마다 실행)
     * 
     * 인기 상품 서비스의 기본적인 동작을 확인합니다.
     * 프로덕션에서는 헬스 체크나 모니터링 시스템과 연동할 수 있습니다.
     */
    @Scheduled(fixedRate = 600000) // 10분 = 600,000ms
    public void healthCheck() {
        try {
            // 기본 인기 상품 조회가 정상적으로 동작하는지 확인
            var popularProducts = popularProductService.getPopularProducts(3, 5);
            
            System.out.printf(
                "[헬스 체크] 인기 상품 서비스 정상 - 현재 인기 상품 %d개 조회됨%n",
                popularProducts.size()
            );
        } catch (Exception e) {
            System.err.println("인기 상품 서비스 헬스 체크 실패: " + e.getMessage());
            
            // 실제 운영에서는 알림 발송이나 장애 대응 로직을 추가할 수 있습니다.
            // 예: alertService.sendAlert("PopularProductService Health Check Failed", e);
        }
    }
}