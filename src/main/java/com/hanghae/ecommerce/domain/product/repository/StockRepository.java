package com.hanghae.ecommerce.domain.product.repository;

import com.hanghae.ecommerce.domain.product.Stock;
import java.util.List;
import java.util.Optional;

/**
 * 재고 Repository 인터페이스
 */
public interface StockRepository {
    
    /**
     * 재고 저장
     */
    Stock save(Stock stock);
    
    /**
     * ID로 재고 조회
     */
    Optional<Stock> findById(Long id);
    
    /**
     * 상품 ID로 재고 조회 (상품 기본 재고)
     */
    Optional<Stock> findByProductId(Long productId);
    
    /**
     * 상품 옵션 ID로 재고 조회 (상품 옵션 재고)
     */
    Optional<Stock> findByProductIdAndProductOptionId(Long productId, Long productOptionId);
    
    /**
     * 상품 ID로 모든 재고 조회 (기본 재고 + 옵션 재고들)
     */
    List<Stock> findAllByProductId(Long productId);
    
    /**
     * 상품 옵션 재고만 조회
     */
    List<Stock> findByProductOptionIdIsNotNull();
    
    /**
     * 상품 기본 재고만 조회 (옵션이 없는 재고)
     */
    List<Stock> findByProductOptionIdIsNull();
    
    /**
     * 재고가 부족한 상품 목록 조회 (임계값 이하)
     */
    List<Stock> findLowStockItems(int threshold);
    
    /**
     * 재고가 없는 상품 목록 조회
     */
    List<Stock> findOutOfStockItems();
    
    /**
     * 모든 재고 조회
     */
    List<Stock> findAll();
    
    /**
     * 재고 존재 여부 확인
     */
    boolean existsById(Long id);
    
    /**
     * 상품 재고 존재 여부 확인
     */
    boolean existsByProductId(Long productId);
    
    /**
     * 상품 옵션 재고 존재 여부 확인
     */
    boolean existsByProductIdAndProductOptionId(Long productId, Long productOptionId);
    
    /**
     * 재고 삭제
     */
    void deleteById(Long id);
    
    /**
     * 특정 상품의 모든 재고 삭제
     */
    void deleteByProductId(Long productId);
    
    /**
     * 전체 재고 항목 수 조회
     */
    long count();
    
    /**
     * 특정 상품의 재고 항목 수 조회
     */
    long countByProductId(Long productId);
    
    /**
     * 상품 ID로 기본 재고 조회 (옵션이 없는 재고)
     */
    Optional<Stock> findByProductIdAndProductOptionIdIsNull(Long productId);
    
    /**
     * 여러 상품 ID로 기본 재고 조회 (옵션이 없는 재고)
     */
    List<Stock> findByProductIdInAndProductOptionIdIsNull(List<Long> productIds);
    
    /**
     * 상품 ID로 기본 재고 조회 (업데이트용 - 락 적용)
     */
    Optional<Stock> findByProductIdAndProductOptionIdIsNullForUpdate(Long productId);
}