package com.pawconnect.repository;

import com.pawconnect.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByBranchIdOrderByCreatedAtDesc(Long branchId);
    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    boolean existsByUserId(Long userId);
    boolean existsByUserIdAndStatusNot(Long userId, String status);
    long countByUserIdAndCouponIdAndStatusNot(Long userId, Long couponId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
