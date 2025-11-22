package com.hanghae.ecommerce.domain.product.repository;

import com.hanghae.ecommerce.domain.product.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * 재고 Repository - Spring Data JPA
 * 
 * 비관적 락을 사용하여 동시성 제어
 */
public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * 상품 ID로 재고 조회
     */
    Optional<Stock> findByProductId(Long productId);

    /**
     * 상품 옵션 ID로 재고 조회
     */
    Optional<Stock> findByProductOptionId(Long productOptionId);

    /**
     * 상품 ID와 옵션 ID로 재고 조회
     */
    Optional<Stock> findByProductIdAndProductOptionId(Long productId, Long productOptionId);

    /**
     * 비관적 락으로 재고 조회 (FOR UPDATE)
     * 동시성 제어를 위한 핵심 메서드
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.id = :id")
    Optional<Stock> findByIdForUpdate(@Param("id") Long id);

    /**
     * 비관적 락으로 상품 ID로 재고 조회
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.productId = :productId")
    Optional<Stock> findByProductIdForUpdate(@Param("productId") Long productId);

    /**
     * 비관적 락으로 상품 옵션 ID로 재고 조회
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.productOptionId = :productOptionId")
    Optional<Stock> findByProductOptionIdForUpdate(@Param("productOptionId") Long productOptionId);

    /**
     * 상품 ID 목록으로 재고 조회
     */
    @Query("SELECT s FROM Stock s WHERE s.productId IN :productIds")
    List<Stock> findByProductIdIn(@Param("productIds") List<Long> productIds);

    /**
     * 상품 ID로 재고 조회 (옵션 없음)
     */
    @Query("SELECT s FROM Stock s WHERE s.productId = :productId AND s.productOptionId IS NULL")
    Optional<Stock> findByProductIdAndProductOptionIdIsNull(@Param("productId") Long productId);

    /**
     * 상품 ID 목록으로 재고 조회 (옵션 없음)
     */
    @Query("SELECT s FROM Stock s WHERE s.productId IN :productIds AND s.productOptionId IS NULL")
    List<Stock> findByProductIdInAndProductOptionIdIsNull(@Param("productIds") List<Long> productIds);

    /**
     * 비관적 락으로 상품 ID로 재고 조회 (옵션 없음)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.productId = :productId AND s.productOptionId IS NULL")
    Optional<Stock> findByProductIdAndProductOptionIdIsNullForUpdate(@Param("productId") Long productId);

    /**
     * 재고 부족 상품 조회 (available_quantity < threshold)
     */
    /*
     * @Query("SELECT s FROM Stock s WHERE s.availableQuantity.value < :threshold")
     * List<Stock> findLowStockProducts(@Param("threshold") int threshold);
     */

    /**
     * 상품 ID로 재고 삭제 (테스트용)
     */
    void deleteByProductId(Long productId);

    // JpaRepository가 자동으로 제공:
    // - Stock save(Stock stock)
    // - Optional<Stock> findById(Long id)
    // - List<Stock> findAll()
    // - void deleteById(Long id)
    // - long count()
}