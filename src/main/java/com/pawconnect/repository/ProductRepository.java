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

    @Transactional
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
    int decreaseStockBy(@Param("id") Long id, @Param("quantity") int quantity);

    @Transactional
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :id")
    int restoreStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Query("SELECT p FROM Product p WHERE (:branchId IS NULL OR p.branch.id = :branchId) " +
           "AND (:suitableSize IS NULL OR p.suitableSize = :suitableSize)")
    List<Product> findByBranchIdAndFilters(
            @Param("branchId") Long branchId, 
            @Param("suitableSize") String suitableSize);
            
    java.util.Optional<Product> findByName(String name);

    java.util.Optional<Product> findByNameAndBranch(String name, com.pawconnect.entity.Branch branch);
}
