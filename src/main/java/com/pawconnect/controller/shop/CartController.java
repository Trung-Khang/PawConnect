package com.pawconnect.controller.shop;

import com.pawconnect.dto.cart.CartResponse;
import com.pawconnect.service.shop.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping
    public CartResponse getMyCart() {
        return cartService.getMyCartResponse();
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/items")
    public CartResponse.CartItemResponse addItemToCart(
            @RequestParam(required = false) Long productId, 
            @RequestParam(required = false) Long puppyListingId, 
            @RequestParam Integer quantity) {
        return cartService.addItem(productId, puppyListingId, quantity);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping("/items/{id}")
    public void removeItemFromCart(@PathVariable Long id) {
        cartService.removeItem(id);
    }
}
