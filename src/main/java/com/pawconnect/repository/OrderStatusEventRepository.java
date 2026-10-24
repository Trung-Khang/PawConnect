package com.pawconnect.repository;

import com.pawconnect.entity.OrderStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusEventRepository extends JpaRepository<OrderStatusEvent, Long> {
}
