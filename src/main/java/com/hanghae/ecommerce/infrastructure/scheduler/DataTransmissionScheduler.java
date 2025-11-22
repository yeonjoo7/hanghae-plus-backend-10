package com.hanghae.ecommerce.infrastructure.scheduler;

import com.hanghae.ecommerce.infrastructure.external.DataTransmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataTransmissionScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(DataTransmissionScheduler.class);
    
    private final DataTransmissionService dataTransmissionService;
    
    public DataTransmissionScheduler(DataTransmissionService dataTransmissionService) {
        this.dataTransmissionService = dataTransmissionService;
    }
    
    /**
     * 5분마다 실패한 데이터 전송을 재시도
     */
    @Scheduled(fixedDelay = 300000) // 5분
    public void retryFailedTransmissions() {
        try {
            log.info("Starting retry for failed data transmissions");
            dataTransmissionService.retryPendingTransmissions();
            log.info("Completed retry for failed data transmissions");
        } catch (Exception e) {
            log.error("Error during data transmission retry", e);
        }
    }
}