package com.pawconnect.repository;

import com.pawconnect.entity.ServiceBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {
    List<ServiceBooking> findByUserId(Long userId);
    List<ServiceBooking> findByUserIdOrderByBookingTimeDesc(Long userId);
    List<ServiceBooking> findAllByOrderByBookingTimeDesc();
    List<ServiceBooking> findByBranchIdOrderByBookingTimeDesc(Long branchId);
    List<ServiceBooking> findByBranchIdAndBookingTimeBetween(Long branchId, LocalDateTime start, LocalDateTime end);
    List<ServiceBooking> findByBranchIdAndBookingTimeBetweenOrderByBookingTimeAsc(Long branchId, LocalDateTime start, LocalDateTime end);
}
