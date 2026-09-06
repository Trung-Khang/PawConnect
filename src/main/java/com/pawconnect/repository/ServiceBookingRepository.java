package com.pawconnect.repository;

import com.pawconnect.entity.ServiceBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {
    List<ServiceBooking> findByUserId(Long userId);
    List<ServiceBooking> findByBranchIdAndBookingTimeBetween(Long branchId, LocalDateTime start, LocalDateTime end);
}
