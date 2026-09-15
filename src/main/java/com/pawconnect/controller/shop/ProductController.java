package com.pawconnect.controller.shop;

import com.pawconnect.dto.product.ProductRequest;
import com.pawconnect.dto.product.ProductResponse;
import com.pawconnect.service.shop.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> getProducts(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Boolean isBreedingDog,
            @RequestParam(required = false) String suitableSize) {
        return productService.getProducts(branchId, isBreedingDog, suitableSize);
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'ADMIN')")
    @PostMapping
    public ProductResponse createProduct(@RequestBody @Valid ProductRequest request) {
        return productService.createProduct(request);
    }

    @PreAuthorize("hasAnyRole('BRANCH_MANAGER', 'ADMIN')")
    @PutMapping("/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @RequestBody @Valid ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }
}
