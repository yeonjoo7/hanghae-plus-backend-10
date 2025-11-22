package com.hanghae.ecommerce.domain.product.repository;

import com.hanghae.ecommerce.domain.product.Product;
import com.hanghae.ecommerce.domain.product.ProductState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 상품 Repository - Spring Data JPA
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 상품명으로 검색 (부분 일치)
     */
    List<Product> findByNameContaining(String name);

    /**
     * 상태별 상품 조회
     */
    List<Product> findByState(ProductState state);

    /**
     * 활성 상품 조회
     */
    @Query("SELECT p FROM Product p WHERE p.state = 'NORMAL' ORDER BY p.createdAt DESC")
    List<Product> findActiveProducts();

    /**
     * ID 목록으로 상품 조회
     */
    List<Product> findByIdIn(List<Long> ids);

    /**
     * 판매 가능한 상품 조회 (state = NORMAL)
     */
    @Query("SELECT p FROM Product p WHERE p.state = 'NORMAL' ORDER BY p.createdAt DESC")
    List<Product> findByStateIsAvailable();

    /**
     * 이름으로 검색 + 판매 가능한 상품만
     */
    @Query("SELECT p FROM Product p WHERE p.name LIKE %:keyword% AND p.state = 'NORMAL' ORDER BY p.createdAt DESC")
    List<Product> findByNameContainingAndStateIsAvailable(@Param("keyword") String keyword);

    /**
     * 상품명 존재 여부 확인
     */
    boolean existsByName(String name);

    /**
     * 상태별 상품 수 조회
     */
    long countByState(ProductState state);

    // JpaRepository가 자동으로 제공:
    // - Product save(Product product)
    // - Optional<Product> findById(Long id)
    // - List<Product> findAll()
    // - void deleteById(Long id)
    // - long count()
}