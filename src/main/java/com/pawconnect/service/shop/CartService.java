package com.pawconnect.service.shop;

import com.pawconnect.entity.Cart;

import com.pawconnect.dto.cart.CartResponse;

public interface CartService {
    Cart getMyCart();
    CartResponse getMyCartResponse();
    CartResponse.CartItemResponse addItem(Long productId, Long puppyListingId, Integer quantity);
    void removeItem(Long itemId);
    void clearCart(Cart cart);
    CartResponse.CartItemResponse updateItemQuantity(Long itemId, Integer quantity);
    void removeItems(Cart cart, java.util.List<com.pawconnect.entity.CartItem> items);
}
