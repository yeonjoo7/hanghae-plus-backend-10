package com.hanghae.ecommerce.domain.product.repository;

import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.ProductState;
import java.util.List;
import java.util.Optional;

/**
 * 상품 Repository 인터페이스
 */
public interface ProductRepository {
    
    /**
     * 상품 저장
     */
    Product save(Product product);
    
    /**
     * ID로 상품 조회
     */
    Optional<Product> findById(Long id);
    
    /**
     * 여러 ID로 상품 목록 조회
     */
    List<Product> findByIdIn(List<Long> ids);
    
    /**
     * 상품명으로 상품 목록 조회 (부분 일치)
     */
    List<Product> findByNameContaining(String name);
    
    /**
     * 상태별 상품 목록 조회
     */
    List<Product> findByState(ProductState state);
    
    /**
     * 판매 가능한 상품 목록 조회
     */
    List<Product> findAvailableProducts();
    
    /**
     * 모든 상품 조회
     */
    List<Product> findAll();
    
    /**
     * 상품 존재 여부 확인
     */
    boolean existsById(Long id);
    
    /**
     * 상품명 존재 여부 확인
     */
    boolean existsByName(String name);
    
    /**
     * 상품 삭제
     */
    void deleteById(Long id);
    
    /**
     * 전체 상품 수 조회
     */
    long count();
    
    /**
     * 상태별 상품 수 조회
     */
    long countByState(ProductState state);
    
    /**
     * 판매 가능한 상품 목록 조회 (별칭)
     */
    List<Product> findByStateIsAvailable();
    
    /**
     * 이름 검색 + 판매 가능한 상품 목록 조회
     */
    List<Product> findByNameContainingAndStateIsAvailable(String keyword);
}