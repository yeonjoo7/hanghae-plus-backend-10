package com.hanghae.ecommerce.application.payment;

import com.hanghae.ecommerce.application.product.ProductRankingService;
import com.hanghae.ecommerce.infrastructure.external.DataTransmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 결제 완료 이벤트 핸들러
 *
 * 트랜잭션 커밋 후 비동기로 부가 로직을 처리한다.
 * - 상품 랭킹 업데이트 (Redis)
 * - 외부 데이터 플랫폼 전송
 */
@Component
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);

    private final ProductRankingService productRankingService;
    private final DataTransmissionService dataTransmissionService;

    public PaymentEventHandler(ProductRankingService productRankingService,
                               DataTransmissionService dataTransmissionService) {
        this.productRankingService = productRankingService;
        this.dataTransmissionService = dataTransmissionService;
    }

    /**
     * 결제 완료 후 부가 로직 처리 (비동기)
     * 트랜잭션 커밋 후에만 실행된다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("결제 완료 이벤트 처리 시작: orderId={}, userId={}", event.orderId(), event.userId());

        // 1. 상품 랭킹 업데이트 (Redis)
        updateProductRanking(event);

        // 2. 외부 데이터 플랫폼 전송
        sendToDataPlatform(event);

        log.info("결제 완료 이벤트 처리 완료: orderId={}", event.orderId());
    }

    private void updateProductRanking(PaymentCompletedEvent event) {
        try {
            productRankingService.incrementOrderCounts(event.productOrderCounts());
        } catch (DataAccessException e) {
            log.error("상품 랭킹 업데이트 실패 (Redis 접근 오류): orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("상품 랭킹 업데이트 실패 (예상치 못한 오류): orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        }
    }

    private void sendToDataPlatform(PaymentCompletedEvent event) {
        try {
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderId", event.orderId());
            orderData.put("userId", event.userId());
            orderData.put("orderNumber", event.orderNumber());
            orderData.put("totalAmount", event.totalAmount());
            orderData.put("discountAmount", 0);
            orderData.put("finalAmount", event.totalAmount());
            orderData.put("paymentMethod", event.paymentMethod().name());
            orderData.put("timestamp", LocalDateTime.now());

            dataTransmissionService.send(orderData);
        } catch (Exception e) {
            log.warn("데이터 전송 실패, Outbox에 저장됨: orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        }
    }
}
