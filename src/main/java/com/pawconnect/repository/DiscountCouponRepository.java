package com.pawconnect.repository;

import com.pawconnect.entity.DiscountCoupon;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountCouponRepository extends JpaRepository<DiscountCoupon, Long> {
    boolean existsByCode(String code);
    List<DiscountCoupon> findAllByOrderByIdAsc();
}
