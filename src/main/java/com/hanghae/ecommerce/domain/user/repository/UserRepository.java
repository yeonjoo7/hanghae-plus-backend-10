package com.hanghae.ecommerce.domain.user.repository;

import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.UserState;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 Repository 인터페이스
 */
public interface UserRepository {
    
    /**
     * 사용자 저장
     */
    User save(User user);
    
    /**
     * ID로 사용자 조회
     */
    Optional<User> findById(Long id);
    
    /**
     * 이메일로 사용자 조회
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 전화번호로 사용자 조회
     */
    Optional<User> findByPhone(String phone);
    
    /**
     * 상태별 사용자 목록 조회
     */
    List<User> findByState(UserState state);
    
    /**
     * 활성 상태 사용자 목록 조회
     */
    List<User> findActiveUsers();
    
    /**
     * 모든 사용자 조회
     */
    List<User> findAll();
    
    /**
     * 사용자 존재 여부 확인 (ID)
     */
    boolean existsById(Long id);
    
    /**
     * 사용자 존재 여부 확인 (이메일)
     */
    boolean existsByEmail(String email);
    
    /**
     * 사용자 존재 여부 확인 (전화번호)
     */
    boolean existsByPhone(String phone);
    
    /**
     * 사용자 삭제
     */
    void deleteById(Long id);
    
    /**
     * 전체 사용자 수 조회
     */
    long count();
    
    /**
     * 상태별 사용자 수 조회
     */
    long countByState(UserState state);
}