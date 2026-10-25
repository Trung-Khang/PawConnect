package com.pawconnect;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.Product;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.security.CustomUserDetails;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;

    private Branch branchA;
    private Branch branchB;
    private Category category;
    private Product productB;

    @BeforeEach
    void setUp() {
        branchA = branchRepository.save(Branch.builder().name("Branch A").code("BRA").address("A Street").build());
        branchB = branchRepository.save(Branch.builder().name("Branch B").code("BRB").address("B Street").build());
        category = categoryRepository.save(Category.builder().name("Food").build());
        productB = productRepository.save(Product.builder().name("Product B").price(BigDecimal.TEN).stock(2)
                .branch(branchB).category(category).build());
    }

    @Test
    void anonymousCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products").contentType("application/json").content(productJson(branchA.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products").with(authentication(managerAuthentication("CUSTOMER", null)))
                        .contentType("application/json").content(productJson(branchA.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void branchManagerCanCreateOwnProductButCannotChangeOtherBranchProduct() throws Exception {
        mockMvc.perform(post("/api/products").with(authentication(managerAuthentication("BRANCH_MANAGER", branchA.getId())))
                        .contentType("application/json").content(productJson(branchA.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/products/{id}", productB.getId())
                        .with(authentication(managerAuthentication("BRANCH_MANAGER", branchA.getId())))
                        .contentType("application/json").content(productJson(branchB.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/{id}", productB.getId())
                        .with(authentication(managerAuthentication("BRANCH_MANAGER", branchA.getId()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products")
                        .with(authentication(managerAuthentication("BRANCH_MANAGER", branchA.getId())))
                        .contentType("application/json").content("[" + productB.getId() + "]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminCanDeleteProductEvenInOwnBranch() throws Exception {
        Product ownProduct = productRepository.save(Product.builder().name("Own product").price(BigDecimal.TEN)
                .stock(2).branch(branchA).category(category).build());

        mockMvc.perform(delete("/api/products/{id}", ownProduct.getId())
                        .with(authentication(managerAuthentication("BRANCH_MANAGER", branchA.getId()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/products/{id}", ownProduct.getId())
                        .with(authentication(managerAuthentication("ADMIN", null))))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken managerAuthentication(String role, Long branchId) {
        CustomUserDetails principal = new CustomUserDetails(99L, branchId, "test@example.com", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private String productJson(Long branchId) {
        return """
                {"name":"Authorized product","price":100,"stock":1,"categoryId":%d,"branchId":%d}
                """.formatted(category.getId(), branchId);
    }
}
