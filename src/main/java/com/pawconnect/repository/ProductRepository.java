package com.pawconnect.repository;

import com.pawconnect.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Transactional
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - 1 WHERE p.id = :id AND p.stock >= 1")
    int decreaseStock(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE p.branch.id = :branchId " +
           "AND (:isBreedingDog IS NULL OR p.isBreedingDog = :isBreedingDog) " +
           "AND (:suitableSize IS NULL OR p.suitableSize = :suitableSize)")
    List<Product> findByBranchIdAndFilters(
            @Param("branchId") Long branchId, 
            @Param("isBreedingDog") Boolean isBreedingDog, 
            @Param("suitableSize") String suitableSize);
}
