package com.hanghae.ecommerce.application.payment;

import com.hanghae.ecommerce.application.product.ProductRankingService;
import com.hanghae.ecommerce.infrastructure.kafka.message.PaymentCompletedMessage;
import com.hanghae.ecommerce.infrastructure.kafka.producer.PaymentEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료 이벤트 핸들러
 *
 * 트랜잭션 커밋 후 비동기로 부가 로직을 처리한다.
 * - 상품 랭킹 업데이트 (Redis)
 * - Kafka 메시지 발행 (외부 데이터 플랫폼 전송용)
 */
@Component
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);

    private final ProductRankingService productRankingService;
    private final PaymentEventProducer paymentEventProducer;

    public PaymentEventHandler(ProductRankingService productRankingService,
            PaymentEventProducer paymentEventProducer) {
        this.productRankingService = productRankingService;
        this.paymentEventProducer = paymentEventProducer;
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

        // 2. Kafka 메시지 발행 (데이터 플랫폼 전송용)
        publishToKafka(event);

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

    /**
     * Kafka로 결제 완료 메시지 발행
     *
     * 트랜잭션이 커밋된 후에만 호출되므로, 결제가 확정된 데이터만 발행된다.
     * Consumer가 메시지를 소비하여 외부 데이터 플랫폼으로 전송한다.
     */
    private void publishToKafka(PaymentCompletedEvent event) {
        try {
            PaymentCompletedMessage message = new PaymentCompletedMessage(
                    event.orderId(),
                    event.userId(),
                    event.orderNumber(),
                    event.totalAmount(),
                    0L, // discountAmount
                    event.totalAmount(), // finalAmount
                    event.paymentMethod().name(),
                    event.productOrderCounts(),
                    event.paidAt()
            );

            paymentEventProducer.sendPaymentCompletedEvent(message);

            log.info("Kafka 메시지 발행 완료: orderId={}, eventId={}",
                    event.orderId(), message.getEventId());

        } catch (Exception e) {
            log.error("Kafka 메시지 발행 실패: orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
            // Kafka 발행 실패 시에도 결제는 이미 완료된 상태
            // 재처리 로직이 필요한 경우 Outbox 패턴 또는 별도 보상 로직 구현
        }
    }
}
