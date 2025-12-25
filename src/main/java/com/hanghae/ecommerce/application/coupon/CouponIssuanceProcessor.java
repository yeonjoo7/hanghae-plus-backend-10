package com.hanghae.ecommerce.application.coupon;

import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.infrastructure.kafka.message.CouponIssuanceRequestMessage;
import com.hanghae.ecommerce.infrastructure.kafka.message.CouponIssuanceResultMessage;
import com.hanghae.ecommerce.infrastructure.kafka.producer.CouponIssuanceProducer;
import com.hanghae.ecommerce.presentation.exception.CouponAlreadyIssuedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 쿠폰 발급 요청 처리 서비스
 *
 * Kafka Consumer로부터 전달받은 발급 요청을 처리하고 결과를 발행한다.
 */
@Service
public class CouponIssuanceProcessor {

    private static final Logger log = LoggerFactory.getLogger(CouponIssuanceProcessor.class);
    private static final String PROCESSED_KEY_PREFIX = "coupon:processed:";

    private final CouponService couponService;
    private final CouponIssuanceProducer couponIssuanceProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    public CouponIssuanceProcessor(CouponService couponService,
                                   CouponIssuanceProducer couponIssuanceProducer,
                                   RedisTemplate<String, Object> redisTemplate) {
        this.couponService = couponService;
        this.couponIssuanceProducer = couponIssuanceProducer;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 쿠폰 발급 요청 처리
     *
     * @param request 발급 요청 메시지
     * @return 처리 성공 여부 (재시도 필요 시 false)
     */
    public boolean process(CouponIssuanceRequestMessage request) {
        log.info("쿠폰 발급 요청 처리: eventId={}, couponId={}, userId={}",
                request.getEventId(), request.getCouponId(), request.getUserId());

        try {
            // 1. 멱등성 체크
            if (isAlreadyProcessed(request.getEventId())) {
                log.info("중복 요청 무시: eventId={}", request.getEventId());
                return true;
            }

            // 2. 실제 쿠폰 발급 처리
            UserCoupon userCoupon = couponService.issueCouponFromQueue(
                    request.getCouponId(),
                    request.getUserId()
            );

            // 3. 처리 완료 마킹
            markAsProcessed(request.getEventId());

            // 4. 성공 결과 발행
            couponIssuanceProducer.sendIssuanceResult(
                    CouponIssuanceResultMessage.success(request, userCoupon.getId())
            );

            log.info("쿠폰 발급 성공: eventId={}, userCouponId={}",
                    request.getEventId(), userCoupon.getId());

            return true;

        } catch (CouponAlreadyIssuedException e) {
            log.info("이미 발급된 쿠폰: eventId={}", request.getEventId());
            markAsProcessed(request.getEventId());
            couponIssuanceProducer.sendIssuanceResult(
                    CouponIssuanceResultMessage.alreadyIssued(request)
            );
            return true;

        } catch (IllegalStateException e) {
            log.info("쿠폰 발급 실패 (소진): eventId={}, message={}",
                    request.getEventId(), e.getMessage());
            markAsProcessed(request.getEventId());
            couponIssuanceProducer.sendIssuanceResult(
                    CouponIssuanceResultMessage.exhausted(request)
            );
            return true;

        } catch (Exception e) {
            log.error("쿠폰 발급 처리 실패: eventId={}, error={}",
                    request.getEventId(), e.getMessage(), e);
            return false;  // 재시도 필요
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        String key = PROCESSED_KEY_PREFIX + eventId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markAsProcessed(String eventId) {
        String key = PROCESSED_KEY_PREFIX + eventId;
        redisTemplate.opsForValue().set(key, "1", Duration.ofDays(1));
    }
}
