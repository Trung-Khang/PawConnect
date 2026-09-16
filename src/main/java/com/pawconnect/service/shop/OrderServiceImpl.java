package com.pawconnect.service.shop;

import com.pawconnect.dto.order.OrderRequest;
import com.pawconnect.dto.order.OrderResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Order;
import com.pawconnect.entity.OrderItem;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.Cart;
import com.pawconnect.entity.CartItem;
import com.pawconnect.entity.PuppyListing;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.OrderRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.PuppyListingRepository;
import com.pawconnect.security.SecurityUtils;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PuppyListingRepository puppyListingRepository;
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
        
        for (CartItem cartItem : cart.getItems()) {
            if (cartItem.getProduct() == null && cartItem.getPuppyListing() == null) {
                throw new BusinessException("Invalid cart item: missing product and puppy listing");
            }
            Branch itemBranch = cartItem.getProduct() != null ? cartItem.getProduct().getBranch() : cartItem.getPuppyListing().getBranch();
            if (!itemBranch.getId().equals(request.getBranchId())) {
                throw new BusinessException("Cart contains item from a different branch");
            }
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
            PuppyListing puppyListing = cartItem.getPuppyListing();
            
            BigDecimal itemPrice = product != null ? product.getPrice() : puppyListing.getPricePerPuppyVnd();
            
            for (int i = 0; i < cartItem.getQuantity(); i++) {
                int updatedRows = 0;
                if (product != null) {
                    updatedRows = productRepository.decreaseStock(product.getId());
                } else if (puppyListing != null) {
                    updatedRows = puppyListingRepository.decrementStock(puppyListing.getId(), 1);
                }
                if (updatedRows == 0) {
                    throw new BusinessException("Item out of stock or race condition");
                }
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .puppyListing(puppyListing)
                    .quantity(cartItem.getQuantity())
                    .price(itemPrice)
                    .build();
            
            order.getOrderItems().add(orderItem);
            totalAmount = totalAmount.add(itemPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);
        
        cartService.clearCart(cart);

        return mapToResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        Long userId = SecurityUtils.getCurrentUserId();
        return orderRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String status) {
        if (!List.of("PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED").contains(status)) {
            throw new BusinessException("Invalid order status: " + status);
        }
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
                if (oi.getProduct() != null) {
                    oir.setProductId(oi.getProduct().getId());
                    oir.setProductName(oi.getProduct().getName());
                } else if (oi.getPuppyListing() != null) {
                    oir.setPuppyListingId(oi.getPuppyListing().getId());
                    oir.setProductName(oi.getPuppyListing().getListingTitle());
                }
                oir.setQuantity(oi.getQuantity());
                oir.setPrice(oi.getPrice());
                return oir;
            }).collect(Collectors.toList()));
        }
        return res;
    }
}
