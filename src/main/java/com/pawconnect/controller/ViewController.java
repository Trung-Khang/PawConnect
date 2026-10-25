package com.pawconnect.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping({"/", "/shop"})
    public String shopPage() {
        return "shop/index"; // points to templates/shop/index.html
    }

    @GetMapping("/service")
    public String bookingPage() {
        return "service/booking"; // points to templates/service/booking.html
    }

    @GetMapping("/admin/products")
    public String adminProductsPage() {
        return "admin/products"; // points to templates/admin/products.html
    }

    @GetMapping("/admin/orders")
    public String adminOrdersPage() {
        return "admin/orders";
    }

    @GetMapping("/admin/users")
    public String adminUsersPage() {
        return "admin/users";
    }

    @GetMapping("/admin/branches")
    public String adminBranchesPage() {
        return "admin/branches";
    }

    @GetMapping("/checkout")
    public String checkoutPage() {
        return "shop/checkout";
    }

    @GetMapping("/orders")
    public String myOrdersPage() {
        return "shop/my-orders";
    }

    @GetMapping("/orders/{orderId:\\d+}/receipt")
    public String receiptPage() {
        return "shop/receipt";
    }

    @GetMapping("/profile")
    public String profilePage() {
        return "profile/index";
    }

    @GetMapping({"/auth/login", "/auth/register", "/auth/verify-email", "/auth/forgot-password"})
    public String authPage() {
        return "auth/index";
    }
}
