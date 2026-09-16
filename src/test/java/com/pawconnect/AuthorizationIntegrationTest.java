package com.pawconnect;

import com.pawconnect.dto.product.ProductRequest;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.Product;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.security.CustomUserDetails;
import com.pawconnect.service.shop.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AuthorizationIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private Branch myBranch;
    private Branch otherBranch;
    private Category category;
    private Product myProduct;
    private Product otherProduct;

    @BeforeEach
    void setUp() {
        myBranch = Branch.builder().name("My Branch").code("MYB").address("123 My St").build();
        otherBranch = Branch.builder().name("Other Branch").code("OTH").address("456 Other St").build();
        branchRepository.save(myBranch);
        branchRepository.save(otherBranch);

        category = Category.builder().name("Dog Food").build();
        categoryRepository.save(category);

        myProduct = Product.builder()
                .name("My Product")
                .price(new BigDecimal("100"))
                .stock(10)
                .branch(myBranch)
                .category(category)
                .build();
        productRepository.save(myProduct);

        otherProduct = Product.builder()
                .name("Other Product")
                .price(new BigDecimal("200"))
                .stock(20)
                .branch(otherBranch)
                .category(category)
                .build();
        productRepository.save(otherProduct);
    }

    private void mockSecurityContext(Long userId, Long branchId, String role) {
        CustomUserDetails userDetails = new CustomUserDetails(
                userId,
                branchId,
                "test@test.com",
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void testBranchManagerCanUpdateOwnProduct() {
        mockSecurityContext(1L, myBranch.getId(), "BRANCH_MANAGER");

        ProductRequest req = new ProductRequest();
        req.setName("Updated My Product");
        req.setPrice(new BigDecimal("150"));
        req.setStock(15);
        req.setBranchId(myBranch.getId());
        req.setCategoryId(category.getId());

        assertDoesNotThrow(() -> productService.updateProduct(myProduct.getId(), req));
    }

    @Test
    void testBranchManagerCannotUpdateOtherProduct() {
        mockSecurityContext(1L, myBranch.getId(), "BRANCH_MANAGER");

        ProductRequest req = new ProductRequest();
        req.setName("Updated Other Product");
        req.setPrice(new BigDecimal("250"));
        req.setStock(25);
        req.setBranchId(otherBranch.getId());
        req.setCategoryId(category.getId());

        assertThrows(AccessDeniedException.class, () -> {
            productService.updateProduct(otherProduct.getId(), req);
        });
    }
    
    @Test
    void testBranchManagerCannotMoveProductToOtherBranch() {
        mockSecurityContext(1L, myBranch.getId(), "BRANCH_MANAGER");

        ProductRequest req = new ProductRequest();
        req.setName("Updated My Product");
        req.setPrice(new BigDecimal("150"));
        req.setStock(15);
        req.setBranchId(otherBranch.getId()); // Try to move to other branch
        req.setCategoryId(category.getId());

        assertThrows(AccessDeniedException.class, () -> {
            productService.updateProduct(myProduct.getId(), req);
        });
    }

    @Test
    void testAdminCanUpdateAnyProduct() {
        mockSecurityContext(2L, null, "ADMIN");

        ProductRequest req = new ProductRequest();
        req.setName("Admin Updated Other Product");
        req.setPrice(new BigDecimal("300"));
        req.setStock(30);
        req.setBranchId(myBranch.getId()); // Admin moves product
        req.setCategoryId(category.getId());

        assertDoesNotThrow(() -> productService.updateProduct(otherProduct.getId(), req));
    }
}
