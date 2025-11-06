package com.hanghae.ecommerce.application.service;

import com.hanghae.ecommerce.domain.payment.BalanceTransaction;
import com.hanghae.ecommerce.domain.payment.repository.BalanceTransactionRepository;
import com.hanghae.ecommerce.domain.user.Point;
import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 사용자 관리 서비스
 * 사용자 정보 조회, 포인트 충전/사용 등의 비즈니스 로직을 처리합니다.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;

    public UserService(UserRepository userRepository, 
                      BalanceTransactionRepository balanceTransactionRepository) {
        this.userRepository = userRepository;
        this.balanceTransactionRepository = balanceTransactionRepository;
    }

    /**
     * 사용자 ID로 조회
     * 
     * @param userId 사용자 ID
     * @return 사용자 정보
     * @throws IllegalArgumentException 사용자를 찾을 수 없는 경우
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));
    }

    /**
     * 이메일로 사용자 조회
     * 
     * @param email 이메일
     * @return 사용자 정보
     * @throws IllegalArgumentException 사용자를 찾을 수 없는 경우
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. 이메일: " + email));
    }

    /**
     * 사용자 등록
     * 
     * @param email 이메일
     * @param name 이름
     * @param phone 전화번호
     * @return 등록된 사용자 정보
     * @throws IllegalArgumentException 이메일이 이미 존재하는 경우
     */
    public User createUser(String email, String name, String phone) {
        // 이메일 중복 체크
        userRepository.findByEmail(email).ifPresent(existingUser -> {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + email);
        });

        User user = User.create(email, name, phone);
        return userRepository.save(user);
    }

    /**
     * 포인트 충전
     * 
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @return 거래 기록
     * @throws IllegalArgumentException 사용자를 찾을 수 없거나 유효하지 않은 금액인 경우
     * @throws IllegalStateException 사용자가 비활성 상태인 경우
     */
    public BalanceTransaction chargePoint(Long userId, Point amount) {
        if (amount == null || amount.getValue() < 1000) {
            throw new IllegalArgumentException("충전 금액은 1,000원 이상이어야 합니다.");
        }

        User user = getUserById(userId);
        Point currentBalance = user.getAvailablePoint();
        
        // 사용자 포인트 충전
        user.chargePoint(amount);
        userRepository.save(user);

        // 거래 기록 생성
        BalanceTransaction transaction = BalanceTransaction.createCharge(
                userId, 
                amount, 
                currentBalance, 
                "잔액 충전"
        );
        return balanceTransactionRepository.save(transaction);
    }

    /**
     * 포인트 사용
     * 
     * @param userId 사용자 ID
     * @param amount 사용 금액
     * @param orderId 주문 ID
     * @param description 사용 내역 설명
     * @return 거래 기록
     * @throws IllegalArgumentException 사용자를 찾을 수 없거나 잔액이 부족한 경우
     * @throws IllegalStateException 사용자가 비활성 상태인 경우
     */
    public BalanceTransaction usePoint(Long userId, Point amount, Long orderId, String description) {
        if (amount == null || !amount.isGreaterThan(Point.zero())) {
            throw new IllegalArgumentException("사용 금액은 0보다 커야 합니다.");
        }

        User user = getUserById(userId);
        Point currentBalance = user.getAvailablePoint();
        
        // 잔액 부족 체크
        if (!currentBalance.isGreaterThanOrEqual(amount)) {
            throw new IllegalArgumentException("잔액이 부족합니다. 현재: " + currentBalance.getValue() + 
                                             ", 필요: " + amount.getValue());
        }

        // 사용자 포인트 사용
        user.usePoint(amount);
        userRepository.save(user);

        // 거래 기록 생성
        BalanceTransaction transaction = BalanceTransaction.createPayment(
                userId, 
                orderId, 
                amount, 
                currentBalance, 
                description != null ? description : "포인트 결제"
        );
        return balanceTransactionRepository.save(transaction);
    }

    /**
     * 포인트 환불
     * 
     * @param userId 사용자 ID
     * @param amount 환불 금액
     * @param orderId 주문 ID
     * @return 거래 기록
     * @throws IllegalArgumentException 사용자를 찾을 수 없거나 유효하지 않은 금액인 경우
     */
    public BalanceTransaction refundPoint(Long userId, Point amount, Long orderId) {
        if (amount == null || !amount.isGreaterThan(Point.zero())) {
            throw new IllegalArgumentException("환불 금액은 0보다 커야 합니다.");
        }

        User user = getUserById(userId);
        Point currentBalance = user.getAvailablePoint();
        
        // 사용자 포인트 환불
        user.refundPoint(amount);
        userRepository.save(user);

        // 거래 기록 생성
        BalanceTransaction transaction = BalanceTransaction.createRefund(
                userId, 
                orderId, 
                amount, 
                currentBalance, 
                "주문 취소로 인한 환불"
        );
        return balanceTransactionRepository.save(transaction);
    }

    /**
     * 사용자 포인트 잔액 조회
     * 
     * @param userId 사용자 ID
     * @return 사용 가능한 포인트
     */
    public Point getUserBalance(Long userId) {
        User user = getUserById(userId);
        return user.getAvailablePoint();
    }

    /**
     * 거래 내역 조회
     * 
     * @param userId 사용자 ID
     * @return 거래 내역 목록
     */
    public List<BalanceTransaction> getTransactionHistory(Long userId) {
        // 사용자 존재 확인
        getUserById(userId);
        return balanceTransactionRepository.findByUserId(userId);
    }

    /**
     * 사용자 프로필 수정
     * 
     * @param userId 사용자 ID
     * @param name 이름
     * @param phone 전화번호
     * @return 수정된 사용자 정보
     */
    public User updateProfile(Long userId, String name, String phone) {
        User user = getUserById(userId);
        user.updateProfile(name, phone);
        return userRepository.save(user);
    }

    /**
     * 사용자 비활성화
     * 
     * @param userId 사용자 ID
     */
    public void deactivateUser(Long userId) {
        User user = getUserById(userId);
        user.deactivate();
        userRepository.save(user);
    }

    /**
     * 사용자 활성화
     * 
     * @param userId 사용자 ID
     */
    public void activateUser(Long userId) {
        User user = getUserById(userId);
        user.activate();
        userRepository.save(user);
    }

    /**
     * 사용자가 활성 상태인지 확인
     * 
     * @param userId 사용자 ID
     * @return 활성 상태 여부
     */
    public boolean isUserActive(Long userId) {
        User user = getUserById(userId);
        return user.isActive();
    }
}