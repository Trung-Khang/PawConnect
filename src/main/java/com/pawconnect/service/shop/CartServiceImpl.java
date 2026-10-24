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
import com.pawconnect.entity.PuppyListing;
import com.pawconnect.entity.ListingStatus;
import com.pawconnect.repository.PuppyListingRepository;
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
    private final PuppyListingRepository puppyListingRepository;

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
    public CartResponse.CartItemResponse addItem(Long productId, Long puppyListingId, Integer quantity) {
        if ((productId == null) == (puppyListingId == null) || quantity == null || quantity < 1) {
            throw new BusinessException("Chọn đúng một sản phẩm và số lượng lớn hơn 0.");
        }
        Cart cart = cartRepository.findByUserIdForUpdate(SecurityUtils.getCurrentUserId()).orElseGet(this::getMyCart);
        
        Product product = null;
        PuppyListing puppyListing = null;
        Integer stock = 0;
        
        if (productId != null) {
            product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            stock = product.getStock();
        } else {
            puppyListing = puppyListingRepository.findById(puppyListingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Puppy listing not found"));
            stock = puppyListing.getStock();
            if (puppyListing.getStatus() != ListingStatus.AVAILABLE) {
                throw new BusinessException("Chó con này hiện không còn được bán.");
            }
        }

        if (stock == null || stock < 1) throw new BusinessException("Sản phẩm hiện đã hết hàng.");

        Long itemBranchId = product != null ? product.getBranch().getId() : puppyListing.getBranch().getId();
        if (cart.getItems().stream().anyMatch(item -> {
            Long branchId = item.getProduct() != null ? item.getProduct().getBranch().getId()
                    : item.getPuppyListing().getBranch().getId();
            return !branchId.equals(itemBranchId);
        })) {
            throw new BusinessException("Mỗi đơn chỉ gồm sản phẩm của một chi nhánh. Vui lòng thanh toán hoặc bỏ sản phẩm trong giỏ trước.");
        }

        // Check if item already in cart
        final Long finalProductId = productId;
        final Long finalPuppyId = puppyListingId;
        CartItem existingItem = cart.getItems().stream()
                .filter(item -> {
                    if (finalProductId != null) {
                        return item.getProduct() != null && item.getProduct().getId().equals(finalProductId);
                    } else {
                        return item.getPuppyListing() != null && item.getPuppyListing().getId().equals(finalPuppyId);
                    }
                })
                .findFirst()
                .orElse(null);

        long totalQuantity = (long) quantity + (existingItem != null ? existingItem.getQuantity() : 0);
        if (stock < totalQuantity) {
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
                    .puppyListing(puppyListing)
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
            res.setBranchId(item.getProduct().getBranch().getId());
            res.setBranchName(item.getProduct().getBranch().getName());
        } else if (item.getPuppyListing() != null) {
            res.setPuppyListingId(item.getPuppyListing().getId());
            res.setProductName(item.getPuppyListing().getListingTitle());
            res.setPrice(item.getPuppyListing().getPricePerPuppyVnd());
            res.setBranchId(item.getPuppyListing().getBranch().getId());
            res.setBranchName(item.getPuppyListing().getBranch().getName());
        }
        res.setQuantity(item.getQuantity());
        return res;
    }
}
