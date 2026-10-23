package com.pawconnect.repository;

import com.pawconnect.entity.PuppyListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface PuppyListingRepository extends JpaRepository<PuppyListing, Long> {
    
    @Transactional
    @Modifying
    @Query("UPDATE PuppyListing p SET p.stock = p.stock - :quantity, p.status = CASE WHEN (p.stock - :quantity) = 0 THEN com.pawconnect.entity.ListingStatus.SOLD_OUT ELSE p.status END WHERE p.id = :id AND p.stock >= :quantity")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Query("SELECT p FROM PuppyListing p WHERE (:branchId IS NULL OR p.branch.id = :branchId) " +
           "AND (:breedCode IS NULL OR p.breedCode = :breedCode) " +
           "AND (:suitableSize IS NULL OR p.expectedAdultSize = :suitableSize)")
    List<PuppyListing> findByBranchIdAndFilters(
            @Param("branchId") Long branchId, 
            @Param("breedCode") String breedCode,
            @Param("suitableSize") String suitableSize);
            
    java.util.Optional<PuppyListing> findBySeedKey(String seedKey);
}
