package com.pawconnect.repository;

import com.pawconnect.entity.OrderCancellationRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCancellationRequestRepository extends JpaRepository<OrderCancellationRequest, Long> {
    List<OrderCancellationRequest> findByOrder_IdOrderByRequestedAtAscIdAsc(Long orderId);
    Optional<OrderCancellationRequest> findFirstByOrder_IdAndStatusOrderByRequestedAtDescIdDesc(Long orderId, String status);
}
