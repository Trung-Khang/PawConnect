package com.pawconnect.controller.shop;

import com.pawconnect.dto.cart.CartResponse;
import com.pawconnect.service.shop.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getMyCart() {
        return cartService.getMyCartResponse();
    }

    @PostMapping("/items")
    public CartResponse.CartItemResponse addItemToCart(@RequestParam Long productId, @RequestParam Integer quantity) {
        return cartService.addItem(productId, quantity);
    }

    @DeleteMapping("/items/{id}")
    public void removeItemFromCart(@PathVariable Long id) {
        cartService.removeItem(id);
    }
}
