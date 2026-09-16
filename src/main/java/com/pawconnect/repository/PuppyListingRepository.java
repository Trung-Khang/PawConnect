package com.pawconnect.repository;

import com.pawconnect.entity.PuppyListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PuppyListingRepository extends JpaRepository<PuppyListing, Long> {
    
    @Modifying
    @Query("UPDATE PuppyListing p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);
}
