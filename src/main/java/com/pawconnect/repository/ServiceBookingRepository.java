package com.pawconnect.repository;

import com.pawconnect.entity.ServiceBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM ServiceBooking b WHERE b.userId = :userId AND b.status = 'COMPLETED' AND LOWER(b.serviceType.name) LIKE '%spa%'")
    boolean hasCompletedSpa(@org.springframework.data.repository.query.Param("userId") Long userId);
    List<ServiceBooking> findByUserId(Long userId);
    List<ServiceBooking> findByUserIdOrderByBookingTimeDesc(Long userId);
    List<ServiceBooking> findAllByOrderByBookingTimeDesc();
    List<ServiceBooking> findByBranchIdOrderByBookingTimeDesc(Long branchId);
    List<ServiceBooking> findByBranchIdAndBookingTimeBetween(Long branchId, LocalDateTime start, LocalDateTime end);
    List<ServiceBooking> findByBranchIdAndBookingTimeBetweenOrderByBookingTimeAsc(Long branchId, LocalDateTime start, LocalDateTime end);
    List<ServiceBooking> findByStatusInAndBookingTimeBefore(List<String> statuses, LocalDateTime threshold);
    boolean existsByUserId(Long userId);
}
