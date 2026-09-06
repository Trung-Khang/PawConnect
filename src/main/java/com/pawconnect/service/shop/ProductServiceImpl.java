package com.pawconnect.service.shop;

import com.pawconnect.dto.product.ProductRequest;
import com.pawconnect.dto.product.ProductResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.Product;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public List<ProductResponse> getProducts(Long branchId, Boolean isBreedingDog, String suitableSize) {
        if (branchId == null) {
            return productRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
        }
        return productRepository.findByBranchIdAndFilters(branchId, isBreedingDog, suitableSize)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public ProductResponse getProductById(Long id) {
        Product p = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
        return mapToResponse(p);
    }

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Product p = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .imageUrl(request.getImageUrl())
                .suitableSize(request.getSuitableSize())
                .isBreedingDog(request.getIsBreedingDog())
                .breed(request.getBreed())
                .age(request.getAge())
                .healthStatus(request.getHealthStatus())
                .careInstructions(request.getCareInstructions())
                .branch(branch)
                .category(category)
                .build();

        productRepository.save(p);
        return mapToResponse(p);
    }

    @Override
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product p = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
        p.setName(request.getName());
        p.setDescription(request.getDescription());
        p.setPrice(request.getPrice());
        p.setStock(request.getStock());
        p.setImageUrl(request.getImageUrl());
        p.setSuitableSize(request.getSuitableSize());
        p.setIsBreedingDog(request.getIsBreedingDog());
        p.setBreed(request.getBreed());
        p.setAge(request.getAge());
        p.setHealthStatus(request.getHealthStatus());
        p.setCareInstructions(request.getCareInstructions());
        
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            p.setCategory(category);
        }
        
        productRepository.save(p);
        return mapToResponse(p);
    }

    @Override
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    private ProductResponse mapToResponse(Product p) {
        ProductResponse res = new ProductResponse();
        res.setId(p.getId());
        res.setName(p.getName());
        res.setDescription(p.getDescription());
        res.setPrice(p.getPrice());
        res.setStock(p.getStock());
        res.setImageUrl(p.getImageUrl());
        res.setSuitableSize(p.getSuitableSize());
        res.setIsBreedingDog(p.getIsBreedingDog());
        res.setBreed(p.getBreed());
        res.setAge(p.getAge());
        res.setHealthStatus(p.getHealthStatus());
        res.setCareInstructions(p.getCareInstructions());
        if (p.getBranch() != null) {
            res.setBranchId(p.getBranch().getId());
        }
        if (p.getCategory() != null) {
            res.setCategoryId(p.getCategory().getId());
            res.setCategoryName(p.getCategory().getName());
        }
        return res;
    }
}
