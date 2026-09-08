package com.pawconnect.service.shop;

import com.pawconnect.entity.Cart;
import com.pawconnect.entity.CartItem;

public interface CartService {
    Cart getMyCart();
    CartItem addItem(Long productId, Integer quantity);
    void removeItem(Long itemId);
    void clearCart(Cart cart);
}
