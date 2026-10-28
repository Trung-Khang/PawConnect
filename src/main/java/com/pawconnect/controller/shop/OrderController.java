package com.pawconnect.controller.shop;

import com.pawconnect.dto.order.OrderRequest;
import com.pawconnect.dto.order.OrderResponse;
import com.pawconnect.service.shop.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public OrderResponse createOrder(@RequestBody @Valid OrderRequest request) {
        return orderService.createOrder(request);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my")
    public List<OrderResponse> getMyOrders() {
        return orderService.getMyOrders();
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'BRANCH_MANAGER', 'ADMIN')")
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/{id}/cancel")
    public OrderResponse cancelMyOrder(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return orderService.cancelMyOrder(id, reason);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/{id}/cancellation-request")
    public OrderResponse requestCancellation(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return orderService.requestCancellation(id, reason);
    }

    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'ADMIN')")
    @PostMapping("/{id}/cancellation-request/approve")
    public OrderResponse approveCancellation(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return orderService.resolveCancellation(id, true, reason);
    }

    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'ADMIN')")
    @PostMapping("/{id}/cancellation-request/reject")
    public OrderResponse rejectCancellation(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return orderService.resolveCancellation(id, false, reason);
    }

    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'ADMIN')")
    @PostMapping("/{id}/confirm-payment")
    public OrderResponse confirmPayment(@PathVariable Long id) {
        return orderService.confirmPayment(id);
    }

    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'ADMIN')")
    @GetMapping("/manage")
    public List<OrderResponse> getManagedOrders() {
        return orderService.getManagedOrders();
    }

    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'ADMIN')")
    @PutMapping("/{id}/status")
    public OrderResponse updateOrderStatus(@PathVariable Long id, @RequestParam String status,
                                           @RequestParam(required = false) String reason) {
        return orderService.updateOrderStatus(id, status, reason);
    }
}
