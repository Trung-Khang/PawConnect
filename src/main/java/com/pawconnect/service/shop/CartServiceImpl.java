package com.pawconnect.service.shop;

import com.pawconnect.entity.Cart;
import com.pawconnect.entity.CartItem;
import com.pawconnect.entity.Product;
import com.pawconnect.repository.CartItemRepository;
import com.pawconnect.repository.CartRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import com.pawconnect.dto.cart.CartResponse;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Override
    public Cart getMyCart() {
        Long userId = SecurityUtils.getCurrentUserId();
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart newCart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
            return cartRepository.save(newCart);
        });
    }

    @Override
    @Transactional
    public CartResponse getMyCartResponse() {
        return mapToCartResponse(getMyCart());
    }

    @Override
    @Transactional
    public CartResponse.CartItemResponse addItem(Long productId, Integer quantity) {
        Cart cart = getMyCart();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Check if item already in cart
        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElse(null);

        int totalQuantity = quantity + (existingItem != null ? existingItem.getQuantity() : 0);
        if (product.getStock() < totalQuantity) {
            throw new BusinessException("Not enough stock available");
        }

        CartItem savedItem;
        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            savedItem = cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .build();
            savedItem = cartItemRepository.save(newItem);
            cart.getItems().add(savedItem);
        }
        return mapToCartItemResponse(savedItem);
    }

    @Override
    @Transactional
    public void removeItem(Long itemId) {
        Cart cart = getMyCart();
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
                
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BusinessException("You don't own this cart item");
        }
        cartItemRepository.delete(item);
    }

    @Override
    @Transactional
    public void clearCart(Cart cart) {
        cartItemRepository.deleteAll(cart.getItems());
        cart.getItems().clear();
    }
    
    private CartResponse mapToCartResponse(Cart cart) {
        CartResponse res = new CartResponse();
        res.setId(cart.getId());
        res.setUserId(cart.getUserId());
        if (cart.getItems() != null) {
            res.setItems(cart.getItems().stream().map(this::mapToCartItemResponse).collect(Collectors.toList()));
        } else {
            res.setItems(new ArrayList<>());
        }
        return res;
    }
    
    private CartResponse.CartItemResponse mapToCartItemResponse(CartItem item) {
        CartResponse.CartItemResponse res = new CartResponse.CartItemResponse();
        res.setId(item.getId());
        if (item.getProduct() != null) {
            res.setProductId(item.getProduct().getId());
            res.setProductName(item.getProduct().getName());
            res.setPrice(item.getProduct().getPrice());
        }
        res.setQuantity(item.getQuantity());
        return res;
    }
}
