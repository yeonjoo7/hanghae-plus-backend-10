package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.coupon.Coupon;
import com.hanghae.ecommerce.domain.coupon.UserCoupon;
import com.hanghae.ecommerce.domain.coupon.UserCouponState;
import com.hanghae.ecommerce.domain.coupon.repository.CouponRepository;
import com.hanghae.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 쿠폰 관리 서비스
 * 쿠폰 발급, 사용, 조회 등의 비즈니스 로직을 처리하며 선착순 로직과 동시성 제어를 포함합니다.
 */
@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    
    // 쿠폰별 동시성 제어를 위한 락 객체 맵
    private final Map<Long, Object> couponLocks = new ConcurrentHashMap<>();

    public CouponService(CouponRepository couponRepository, 
                        UserCouponRepository userCouponRepository,
                        UserRepository userRepository) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userRepository = userRepository;
    }

    /**
     * 쿠폰 조회
     * 
     * @param couponId 쿠폰 ID
     * @return 쿠폰 정보
     * @throws IllegalArgumentException 쿠폰을 찾을 수 없는 경우
     */
    public Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. ID: " + couponId));
    }

    /**
     * 선착순 쿠폰 발급 (동시성 제어)
     * 
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰
     * @throws IllegalArgumentException 쿠폰/사용자를 찾을 수 없는 경우
     * @throws IllegalStateException 쿠폰을 발급할 수 없는 경우
     */
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        // 사용자 존재 및 활성 상태 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));
        
        if (!user.isActive()) {
            throw new IllegalStateException("비활성 사용자는 쿠폰을 발급받을 수 없습니다.");
        }

        // 쿠폰별 락 획득 (선착순 처리를 위한 동시성 제어)
        Object lock = couponLocks.computeIfAbsent(couponId, k -> new Object());
        
        synchronized (lock) {
            // 쿠폰 존재 확인
            Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다. ID: " + couponId));

            // 중복 발급 확인
            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
            }

            // 쿠폰 발급 가능 여부 확인
            if (!coupon.canIssue()) {
                if (!coupon.isWithinValidPeriod()) {
                    throw new IllegalStateException("쿠폰 발급 기간이 아닙니다.");
                } else if (!coupon.hasRemainingQuantity()) {
                    throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
                } else {
                    throw new IllegalStateException("발급할 수 없는 쿠폰입니다. 상태: " + coupon.getState());
                }
            }

            // 쿠폰 발급 수량 증가
            coupon.issue();
            couponRepository.save(coupon);

            // 사용자 쿠폰 생성
            UserCoupon userCoupon = UserCoupon.issue(userId, couponId, coupon.getEndDate());
            return userCouponRepository.save(userCoupon);
        }
    }

    /**
     * 사용자 보유 쿠폰 목록 조회
     * 
     * @param userId 사용자 ID
     * @return 보유 쿠폰 목록
     */
    public List<UserCouponInfo> getUserCoupons(Long userId) {
        // 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        List<UserCoupon> userCoupons = userCouponRepository.findByUserIdOrderByIssuedAtDesc(userId);
        
        return userCoupons.stream()
                .map(this::createUserCouponInfo)
                .collect(Collectors.toList());
    }

    /**
     * 사용 가능한 쿠폰 목록 조회
     * 
     * @param userId 사용자 ID
     * @return 사용 가능한 쿠폰 목록
     */
    public List<UserCouponInfo> getAvailableUserCoupons(Long userId) {
        // 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        List<UserCoupon> availableCoupons = userCouponRepository
                .findByUserIdAndStateOrderByIssuedAtDesc(userId, UserCouponState.AVAILABLE);
        
        return availableCoupons.stream()
                .filter(UserCoupon::canUse) // 만료되지 않은 쿠폰만
                .map(this::createUserCouponInfo)
                .collect(Collectors.toList());
    }

    /**
     * 쿠폰 사용 처리
     * 
     * @param userId 사용자 ID
     * @param userCouponId 사용자 쿠폰 ID
     * @return 사용된 사용자 쿠폰
     * @throws IllegalArgumentException 쿠폰을 찾을 수 없는 경우
     * @throws IllegalStateException 쿠폰을 사용할 수 없는 경우
     */
    public UserCoupon useCoupon(Long userId, Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다. ID: " + userCouponId));
        
        // 소유권 확인
        if (!userCoupon.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 쿠폰이 아닙니다.");
        }

        // 쿠폰 사용
        userCoupon.use();
        return userCouponRepository.save(userCoupon);
    }

    /**
     * 사용자 쿠폰 상세 조회
     * 
     * @param userId 사용자 ID
     * @param userCouponId 사용자 쿠폰 ID
     * @return 사용자 쿠폰 상세 정보
     */
    public UserCouponInfo getUserCoupon(Long userId, Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다. ID: " + userCouponId));
        
        // 소유권 확인
        if (!userCoupon.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 쿠폰이 아닙니다.");
        }

        return createUserCouponInfo(userCoupon);
    }

    /**
     * 쿠폰 사용 이력 조회
     * 
     * @param userId 사용자 ID
     * @return 사용된 쿠폰 목록
     */
    public List<UserCouponInfo> getCouponUsageHistory(Long userId) {
        // 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        List<UserCoupon> usedCoupons = userCouponRepository
                .findByUserIdAndStateOrderByUsedAtDesc(userId, UserCouponState.USED);
        
        return usedCoupons.stream()
                .map(this::createUserCouponInfo)
                .collect(Collectors.toList());
    }

    /**
     * 발급 가능한 쿠폰 목록 조회
     * 
     * @return 발급 가능한 쿠폰 목록
     */
    public List<Coupon> getAvailableCoupons() {
        return couponRepository.findIssuableCoupons();
    }

    /**
     * 만료된 쿠폰 일괄 처리 (배치 작업용)
     * 
     * @return 처리된 쿠폰 수
     */
    public int expireOutdatedCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> expiredCoupons = userCouponRepository.findExpiredAvailableCoupons(now);
        
        for (UserCoupon userCoupon : expiredCoupons) {
            userCoupon.expire();
        }
        
        userCouponRepository.saveAll(expiredCoupons);
        return expiredCoupons.size();
    }

    /**
     * 쿠폰 발급 통계 조회 (관리자용)
     * 
     * @param couponId 쿠폰 ID
     * @return 쿠폰 발급 통계
     */
    public CouponStatistics getCouponStatistics(Long couponId) {
        Coupon coupon = getCoupon(couponId);
        
        int totalIssued = coupon.getIssuedQuantity().getValue();
        int totalUsed = userCouponRepository.countByStateAndCouponId(UserCouponState.USED, couponId);
        int totalExpired = userCouponRepository.countByStateAndCouponId(UserCouponState.EXPIRED, couponId);
        int totalAvailable = userCouponRepository.countByStateAndCouponId(UserCouponState.AVAILABLE, couponId);
        
        double usageRate = totalIssued > 0 ? (double) totalUsed / totalIssued * 100 : 0.0;
        
        return new CouponStatistics(
                couponId,
                coupon.getName(),
                coupon.getTotalQuantity().getValue(),
                totalIssued,
                totalUsed,
                totalExpired,
                totalAvailable,
                usageRate
        );
    }

    /**
     * 사용자 쿠폰 정보를 생성하는 헬퍼 메서드
     */
    private UserCouponInfo createUserCouponInfo(UserCoupon userCoupon) {
        Coupon coupon = getCoupon(userCoupon.getCouponId());
        return new UserCouponInfo(userCoupon, coupon);
    }

    /**
     * 사용자 쿠폰 정보를 담는 클래스
     */
    public static class UserCouponInfo {
        private final UserCoupon userCoupon;
        private final Coupon coupon;

        public UserCouponInfo(UserCoupon userCoupon, Coupon coupon) {
            this.userCoupon = userCoupon;
            this.coupon = coupon;
        }

        public UserCoupon getUserCoupon() {
            return userCoupon;
        }

        public Coupon getCoupon() {
            return coupon;
        }

        public Long getUserCouponId() {
            return userCoupon.getId();
        }

        public Long getCouponId() {
            return coupon.getId();
        }

        public String getCouponName() {
            return coupon.getName();
        }

        public UserCouponState getState() {
            return userCoupon.getState();
        }

        public boolean canUse() {
            return userCoupon.canUse();
        }

        public boolean isExpired() {
            return userCoupon.isExpired();
        }
    }

    /**
     * 쿠폰 발급 통계 정보를 담는 클래스
     */
    public static class CouponStatistics {
        private final Long couponId;
        private final String couponName;
        private final int totalQuantity;
        private final int totalIssued;
        private final int totalUsed;
        private final int totalExpired;
        private final int totalAvailable;
        private final double usageRate;

        public CouponStatistics(Long couponId, String couponName, int totalQuantity, 
                              int totalIssued, int totalUsed, int totalExpired, 
                              int totalAvailable, double usageRate) {
            this.couponId = couponId;
            this.couponName = couponName;
            this.totalQuantity = totalQuantity;
            this.totalIssued = totalIssued;
            this.totalUsed = totalUsed;
            this.totalExpired = totalExpired;
            this.totalAvailable = totalAvailable;
            this.usageRate = usageRate;
        }

        public Long getCouponId() {
            return couponId;
        }

        public String getCouponName() {
            return couponName;
        }

        public int getTotalQuantity() {
            return totalQuantity;
        }

        public int getTotalIssued() {
            return totalIssued;
        }

        public int getTotalUsed() {
            return totalUsed;
        }

        public int getTotalExpired() {
            return totalExpired;
        }

        public int getTotalAvailable() {
            return totalAvailable;
        }

        public double getUsageRate() {
            return usageRate;
        }

        public int getRemainingQuantity() {
            return totalQuantity - totalIssued;
        }
    }
}