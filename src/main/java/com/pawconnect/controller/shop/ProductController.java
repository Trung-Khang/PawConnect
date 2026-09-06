package com.pawconnect.controller.shop;

import com.pawconnect.dto.product.ProductRequest;
import com.pawconnect.dto.product.ProductResponse;
import com.pawconnect.service.shop.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public ProductResponse createProduct(@RequestBody ProductRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }
}
