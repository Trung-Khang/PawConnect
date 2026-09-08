package com.pawconnect.service.shop;

import com.pawconnect.dto.order.OrderRequest;
import com.pawconnect.dto.order.OrderResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Order;
import com.pawconnect.entity.OrderItem;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.Cart;
import com.pawconnect.entity.CartItem;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.OrderRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.security.SecurityUtils;
import org.springframework.transaction.annotation.Transactional;
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
    private final CartService cartService;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        Cart cart = cartService.getMyCart();
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessException("Cart is empty");
        }

        Order order = Order.builder()
                .userId(userId)
                .branch(branch)
                .status("PENDING")
                .orderItems(new ArrayList<>())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            
            for (int i = 0; i < cartItem.getQuantity(); i++) {
                int updatedRows = productRepository.decreaseStock(product.getId());
                if (updatedRows == 0) {
                    throw new BusinessException("Product out of stock or race condition: " + product.getName());
                }
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .price(product.getPrice())
                    .build();
            
            order.getOrderItems().add(orderItem);
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);
        
        cartService.clearCart(cart);

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
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
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
