package com.hanghae.ecommerce.presentation.controller.coupon;

import com.hanghae.ecommerce.application.coupon.CouponService;
import com.hanghae.ecommerce.common.ApiResponse;
import com.hanghae.ecommerce.presentation.dto.CouponUsageHistoryResponse;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponInfo;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import com.hanghae.ecommerce.presentation.dto.IssueCouponResponse;
import com.hanghae.ecommerce.presentation.dto.MyCouponResponse;
import com.hanghae.ecommerce.presentation.dto.RequestCouponIssueResponse;
import com.hanghae.ecommerce.presentation.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 쿠폰 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    // TODO: 현재는 임시로 userId를 1L로 고정. 실제로는 인증된 사용자 정보에서 가져와야 함
    private static final Long CURRENT_USER_ID = 1L;

    /**
     * 쿠폰 발급 요청 (Redis 기반 비동기)
     * POST /coupons/{couponId}/request
     * 
     * Redis 대기열에 추가하고 즉시 응답합니다.
     * 실제 발급은 스케줄러가 비동기로 처리합니다.
     */
    @PostMapping("/{couponId}/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RequestCouponIssueResponse> requestCouponIssue(@PathVariable Long couponId) {
        try {
            long queueRank = couponService.requestCouponIssue(couponId, CURRENT_USER_ID);
            long queueSize = couponService.getQueueSize(couponId);

            RequestCouponIssueResponse response = new RequestCouponIssueResponse(
                    couponId,
                    queueRank,
                    queueSize);

            return ApiResponse.success(response, "쿠폰 발급 요청이 대기열에 추가되었습니다");

        } catch (CouponNotFoundException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("사용자를 찾을 수 없습니다")) {
                throw new IllegalArgumentException("사용자를 찾을 수 없습니다");
            }
            throw e;
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("쿠폰을 발급할 수 없습니다")) {
                throw new IllegalStateException("쿠폰을 발급할 수 없습니다");
            }
            throw e;
        } catch (CouponAlreadyIssuedException e) {
            throw e;
        }
    }

    /**
     * 쿠폰 발급 (기존 동기 방식 - 하위 호환성 유지)
     * POST /coupons/{couponId}/issue
     */
    @PostMapping("/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IssueCouponResponse> issueCoupon(@PathVariable Long couponId) {
        try {
            UserCoupon userCoupon = couponService.issueCoupon(CURRENT_USER_ID, couponId);
            UserCouponInfo couponInfo = couponService.getUserCoupon(CURRENT_USER_ID, userCoupon.getId());

            IssueCouponResponse response = new IssueCouponResponse(
                    couponInfo.getUserCouponId(),
                    couponInfo.getCouponId(),
                    couponInfo.getCouponName(),
                    couponInfo.getCoupon().getDiscountPolicy().getType().name(),
                    couponInfo.getCoupon().getDiscountPolicy().getDiscountType().name(),
                    couponInfo.getCoupon().getDiscountPolicy().getDiscountValue(),
                    couponInfo.getCoupon().getDiscountPolicy().hasMinOrderAmount()
                            ? couponInfo.getCoupon().getDiscountPolicy().getMinOrderAmount().getValue()
                            : 0,
                    couponInfo.getUserCoupon().getExpirationDate(),
                    couponInfo.getUserCoupon().getIssuedAt());

            return ApiResponse.success(response, "쿠폰이 발급되었습니다");

        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("쿠폰을 찾을 수 없습니다")) {
                throw new CouponNotFoundException(couponId);
            }
            throw e;
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("이미 발급받은 쿠폰입니다")) {
                throw new CouponAlreadyIssuedException();
            } else if (e.getMessage().contains("쿠폰이 모두 소진되었습니다")) {
                throw new CouponSoldOutException();
            }
            throw e;
        }
    }

    /**
     * 보유 쿠폰 조회
     * GET /coupons/my
     */
    @GetMapping("/my")
    public ApiResponse<MyCouponResponse> getMyCoupons(
            @RequestParam(required = false) String status) {

        List<UserCouponInfo> couponInfos;
        if (status != null) {
            UserCouponState couponStatus = parseUserCouponStatus(status);
            // 상태별 필터링 구현 필요 - 현재는 전체 조회 후 필터링
            couponInfos = couponService.getUserCoupons(CURRENT_USER_ID)
                    .stream()
                    .filter(info -> info.getState() == couponStatus)
                    .collect(Collectors.toList());
        } else {
            couponInfos = couponService.getUserCoupons(CURRENT_USER_ID);
        }

        List<MyCouponResponse.CouponResponse> coupons = couponInfos.stream()
                .map(this::toCouponResponse)
                .collect(Collectors.toList());

        int totalCount = coupons.size();
        int availableCount = (int) couponInfos.stream()
                .filter(UserCouponInfo::canUse)
                .count();

        MyCouponResponse response = new MyCouponResponse(coupons, totalCount, availableCount);
        return ApiResponse.success(response);
    }

    /**
     * 대기열 순위 조회
     * GET /coupons/{couponId}/queue-rank
     */
    @GetMapping("/{couponId}/queue-rank")
    public ApiResponse<RequestCouponIssueResponse> getQueueRank(@PathVariable Long couponId) {
        long queueRank = couponService.getQueueRank(couponId, CURRENT_USER_ID);
        long queueSize = couponService.getQueueSize(couponId);

        RequestCouponIssueResponse response = new RequestCouponIssueResponse(
                couponId,
                queueRank > 0 ? queueRank : null,
                queueSize);

        return ApiResponse.success(response);
    }

    /**
     * 쿠폰 사용 이력 조회
     * GET /coupons/usage-history
     */
    @GetMapping("/usage-history")
    public ApiResponse<CouponUsageHistoryResponse> getCouponUsageHistory(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        // 기본값 설정
        int pageNum = page != null ? page : 1;
        int pageSize = size != null ? size : 20;

        List<UserCouponInfo> usageHistory = couponService.getCouponUsageHistory(CURRENT_USER_ID);

        // 페이징 처리 (간단한 구현)
        int totalItems = usageHistory.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalItems);

        List<UserCouponInfo> paginatedHistory = usageHistory.subList(startIndex, endIndex);

        List<CouponUsageHistoryResponse.UsageHistoryItem> items = paginatedHistory.stream()
                .map(info -> new CouponUsageHistoryResponse.UsageHistoryItem(
                        info.getUserCouponId(),
                        info.getCouponName(),
                        null, // orderId - 실제로는 쿠폰 사용 시점의 주문 정보를 저장해야 함
                        null, // orderNumber
                        50000, // discountAmount - 실제로는 계산된 할인 금액을 저장해야 함
                        info.getUserCoupon().getUsedAt()))
                .collect(Collectors.toList());

        CouponUsageHistoryResponse.Pagination pagination = new CouponUsageHistoryResponse.Pagination(pageNum,
                totalPages, totalItems, pageSize);

        CouponUsageHistoryResponse response = new CouponUsageHistoryResponse(items, pagination);
        return ApiResponse.success(response);
    }

    /**
     * UserCouponInfo를 CouponResponse로 변환
     */
    private MyCouponResponse.CouponResponse toCouponResponse(UserCouponInfo couponInfo) {
        return new MyCouponResponse.CouponResponse(
                couponInfo.getUserCouponId(),
                couponInfo.getCouponId(),
                couponInfo.getCouponName(),
                couponInfo.getCoupon().getDiscountPolicy().getType().name(),
                couponInfo.getCoupon().getDiscountPolicy().getDiscountType().name(),
                couponInfo.getCoupon().getDiscountPolicy().getDiscountValue(),
                couponInfo.getCoupon().getDiscountPolicy().hasMinOrderAmount()
                        ? couponInfo.getCoupon().getDiscountPolicy().getMinOrderAmount().getValue()
                        : 0,
                couponInfo.getCoupon().getDiscountPolicy().getApplicableProductIds(),
                mapUserCouponStatus(couponInfo.getState()),
                couponInfo.getUserCoupon().getExpirationDate(),
                couponInfo.getUserCoupon().getIssuedAt(),
                couponInfo.getUserCoupon().getUsedAt());
    }

    /**
     * 쿠폰 상태 문자열을 UserCouponState로 변환
     */
    private UserCouponState parseUserCouponStatus(String status) {
        switch (status.toUpperCase()) {
            case "AVAILABLE":
                return UserCouponState.AVAILABLE;
            case "USED":
                return UserCouponState.USED;
            case "EXPIRED":
                return UserCouponState.EXPIRED;
            default:
                throw new IllegalArgumentException("유효하지 않은 쿠폰 상태입니다: " + status);
        }
    }

    /**
     * UserCouponState를 API 응답용 문자열로 변환
     */
    private String mapUserCouponStatus(UserCouponState state) {
        switch (state) {
            case AVAILABLE:
                return "AVAILABLE";
            case USED:
                return "USED";
            case EXPIRED:
                return "EXPIRED";
            default:
                return state.name();
        }
    }
}