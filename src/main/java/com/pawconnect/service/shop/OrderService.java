package com.pawconnect.service.shop;

import com.pawconnect.dto.order.OrderRequest;
import com.pawconnect.dto.order.OrderResponse;
import java.util.List;

public interface OrderService {
    OrderResponse createOrder(OrderRequest request);
    List<OrderResponse> getMyOrders();
    List<OrderResponse> getManagedOrders();
    OrderResponse getOrder(Long orderId);
    OrderResponse cancelMyOrder(Long orderId, String reason);
    OrderResponse requestCancellation(Long orderId, String reason);
    OrderResponse resolveCancellation(Long orderId, boolean approve, String reason);
    OrderResponse confirmPayment(Long orderId);
    OrderResponse updateOrderStatus(Long orderId, String status, String reason);
}
