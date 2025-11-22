package com.hanghae.ecommerce.domain.product.repository;

import com.hanghae.ecommerce.domain.product.ProductOption;
import com.hanghae.ecommerce.domain.product.ProductState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 상품 옵션 Repository - Spring Data JPA
 */
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

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
    /*
     * @Query("SELECT po FROM ProductOption po WHERE po.state = com.hanghae.ecommerce.domain.product.ProductState.NORMAL ORDER BY po.createdAt DESC"
     * )
     * List<ProductOption> findAvailableOptions();
     * 
     * @Query("SELECT po FROM ProductOption po WHERE po.productId = :productId AND po.state = com.hanghae.ecommerce.domain.product.ProductState.NORMAL ORDER BY po.createdAt DESC"
     * )
     * List<ProductOption> findAvailableOptionsByProductId(@Param("productId") Long
     * productId);
     */

    /**
     * 특정 상품에 옵션이 존재하는지 확인
     */
    boolean existsByProductId(Long productId);

    /**
     * 특정 상품의 모든 옵션 삭제
     */
    @Modifying
    @Query("DELETE FROM ProductOption po WHERE po.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    /**
     * 상품별 옵션 수 조회
     */
    long countByProductId(Long productId);

    /**
     * 상태별 상품 옵션 수 조회
     */
    long countByState(ProductState state);
}