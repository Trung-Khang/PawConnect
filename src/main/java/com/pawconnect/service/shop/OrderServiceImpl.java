package com.pawconnect.service.shop;

import com.pawconnect.dto.order.OrderRequest;
import com.pawconnect.dto.order.OrderResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Order;
import com.pawconnect.entity.OrderItem;
import com.pawconnect.entity.Product;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.OrderRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.security.SecurityUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        Order order = Order.builder()
                .userId(userId)
                .branch(branch)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .orderItems(new ArrayList<>())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        // Atomic decrease logic and OrderItem creation
        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            
            for (int i = 0; i < itemReq.getQuantity(); i++) {
                int updatedRows = productRepository.decreaseStock(product.getId());
                if (updatedRows == 0) {
                    throw new RuntimeException("Product out of stock or race condition: " + product.getName());
                }
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .price(product.getPrice())
                    .build();
            
            order.getOrderItems().add(orderItem);
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);

        return mapToResponse(order);
    }

    @Override
    public List<OrderResponse> getMyOrders() {
        Long userId = SecurityUtils.getCurrentUserId();
        return orderRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public OrderResponse updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(status);
        orderRepository.save(order);
        return mapToResponse(order);
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse res = new OrderResponse();
        res.setId(order.getId());
        res.setUserId(order.getUserId());
        res.setBranchId(order.getBranch().getId());
        res.setStatus(order.getStatus());
        res.setTotalAmount(order.getTotalAmount());
        res.setCreatedAt(order.getCreatedAt());
        if (order.getOrderItems() != null) {
            res.setItems(order.getOrderItems().stream().map(oi -> {
                OrderResponse.OrderItemResponse oir = new OrderResponse.OrderItemResponse();
                oir.setId(oi.getId());
                oir.setProductId(oi.getProduct().getId());
                oir.setProductName(oi.getProduct().getName());
                oir.setQuantity(oi.getQuantity());
                oir.setPrice(oi.getPrice());
                return oir;
            }).collect(Collectors.toList()));
        }
        return res;
    }
}
