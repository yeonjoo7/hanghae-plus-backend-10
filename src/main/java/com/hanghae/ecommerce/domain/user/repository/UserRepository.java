package com.hanghae.ecommerce.domain.user.repository;

import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.UserState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 Repository - Spring Data JPA
 * 
 * 기존 181줄의 JdbcTemplate 코드를 Spring Data JPA로 대체
 * - save(), findById(), deleteById(), count() 등은 JpaRepository가 자동 제공
 * - 메서드 이름 기반 쿼리 자동 생성 (findByEmail, existsByEmail 등)
 * - 복잡한 쿼리는 @Query로 명시
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 사용자 조회
     * Spring Data JPA가 자동으로 쿼리 생성: SELECT * FROM users WHERE email = ?
     */
    Optional<User> findByEmail(String email);

    /**
     * 전화번호로 사용자 조회
     * Spring Data JPA가 자동으로 쿼리 생성: SELECT * FROM users WHERE phone = ?
     */
    Optional<User> findByPhone(String phone);

    /**
     * 상태별 사용자 목록 조회
     * Spring Data JPA가 자동으로 쿼리 생성: SELECT * FROM users WHERE state = ?
     */
    List<User> findByState(UserState state);

    /**
     * 활성 상태 사용자 목록 조회
     * JPQL을 사용한 커스텀 쿼리
     */
    @Query("SELECT u FROM User u WHERE u.state = com.hanghae.ecommerce.domain.user.UserState.NORMAL")
    List<User> findActiveUsers();

    /**
     * 비관적 락을 사용한 사용자 조회 (동시성 제어)
     * 잔액 차감 등 동시성 문제가 발생할 수 있는 경우 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);

    /**
     * 활성 상태 사용자 목록 조회
     * JPQL을 사용한 커스텀 쿼리
     */
    /*
     * @Query("SELECT u FROM User u WHERE u.email = :email AND u.state = com.hanghae.ecommerce.domain.user.UserState.NORMAL"
     * )
     * Optional<User> findActiveUserByEmail(@Param("email") String email);
     */

    /**
     * 이메일 존재 여부 확인
     * Spring Data JPA가 자동으로 쿼리 생성: SELECT COUNT(*) > 0 FROM users WHERE email = ?
     */
    boolean existsByEmail(String email);

    /**
     * 전화번호 존재 여부 확인
     * Spring Data JPA가 자동으로 쿼리 생성: SELECT COUNT(*) > 0 FROM users WHERE phone = ?
     */
    boolean existsByPhone(String phone);

    /**
     * 상태별 사용자 수 조회
     * Spring Data JPA가 자동으로 쿼리 생성: SELECT COUNT(*) FROM users WHERE state = ?
     */
    long countByState(UserState state);

    // JpaRepository가 자동으로 제공하는 메서드들:
    // - User save(User user)
    // - Optional<User> findById(Long id)
    // - List<User> findAll()
    // - boolean existsById(Long id)
    // - void deleteById(Long id)
    // - long count()
    // - void delete(User user)
    // - void deleteAll()
    // 등 많은 메서드들이 자동으로 제공됨!
}