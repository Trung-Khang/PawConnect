package com.pawconnect.repository;

import com.pawconnect.entity.OrderStatusEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusEventRepository extends JpaRepository<OrderStatusEvent, Long> {
    List<OrderStatusEvent> findByOrder_IdOrderByCreatedAtAscIdAsc(Long orderId);
}
