package com.hanghae.ecommerce.domain.product.repository;

import com.hanghae.ecommerce.domain.product.ProductOption;
import com.hanghae.ecommerce.domain.product.ProductState;
import java.util.List;
import java.util.Optional;

/**
 * 상품 옵션 Repository 인터페이스
 */
public interface ProductOptionRepository {
    
    /**
     * 상품 옵션 저장
     */
    ProductOption save(ProductOption productOption);
    
    /**
     * ID로 상품 옵션 조회
     */
    Optional<ProductOption> findById(Long id);
    
    /**
     * 상품 ID로 상품 옵션 목록 조회
     */
    List<ProductOption> findByProductId(Long productId);
    
    /**
     * 상품 ID와 상태로 상품 옵션 목록 조회
     */
    List<ProductOption> findByProductIdAndState(Long productId, ProductState state);
    
    /**
     * 상태별 상품 옵션 목록 조회
     */
    List<ProductOption> findByState(ProductState state);
    
    /**
     * 사용 가능한 상품 옵션 목록 조회 (전체)
     */
    List<ProductOption> findAvailableOptions();
    
    /**
     * 특정 상품의 사용 가능한 옵션 목록 조회
     */
    List<ProductOption> findAvailableOptionsByProductId(Long productId);
    
    /**
     * 모든 상품 옵션 조회
     */
    List<ProductOption> findAll();
    
    /**
     * 상품 옵션 존재 여부 확인
     */
    boolean existsById(Long id);
    
    /**
     * 특정 상품에 옵션이 존재하는지 확인
     */
    boolean existsByProductId(Long productId);
    
    /**
     * 상품 옵션 삭제
     */
    void deleteById(Long id);
    
    /**
     * 특정 상품의 모든 옵션 삭제
     */
    void deleteByProductId(Long productId);
    
    /**
     * 전체 상품 옵션 수 조회
     */
    long count();
    
    /**
     * 상품별 옵션 수 조회
     */
    long countByProductId(Long productId);
    
    /**
     * 상태별 상품 옵션 수 조회
     */
    long countByState(ProductState state);
}