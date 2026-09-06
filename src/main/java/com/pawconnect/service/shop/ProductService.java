package com.pawconnect.service.shop;

import com.pawconnect.dto.product.ProductRequest;
import com.pawconnect.dto.product.ProductResponse;
import java.util.List;

public interface ProductService {
    List<ProductResponse> getProducts(Long branchId, Boolean isBreedingDog, String suitableSize);
    ProductResponse getProductById(Long id);
    ProductResponse createProduct(ProductRequest request);
    ProductResponse updateProduct(Long id, ProductRequest request);
    void deleteProduct(Long id);
}
