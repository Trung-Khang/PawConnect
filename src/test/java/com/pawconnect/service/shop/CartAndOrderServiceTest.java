package com.pawconnect.service.shop;

import com.pawconnect.dto.order.OrderResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.Product;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@org.springframework.transaction.annotation.Transactional
public class CartAndOrderServiceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long productId;
    private Long branchId;

    @BeforeEach
    public void setup() {
        Branch branch = Branch.builder().name("Test Branch").address("Address").build();
        branch = branchRepository.save(branch);
        branchId = branch.getId();

        Category cat = Category.builder().name("Dog Food").build();
        cat = categoryRepository.save(cat);

        Product p = Product.builder()
                .name("Premium Dog Food")
                .price(new BigDecimal("200000"))
                .stock(100)
                .branch(branch)
                .category(cat)
                .isBreedingDog(false)
                .build();
        p = productRepository.save(p);
        productId = p.getId();
    }

    @AfterEach
    public void cleanup() {
        // Tránh lỗi foreign key constraint, xóa từ con lên cha nhưng vì Test Transactional, có thể truncate
    }

    @Test
    public void testCartAndCheckoutFlow() {
        // 1. Thêm vào giỏ hàng
        cartService.addItem(productId, 2); // Thêm 2 bịch thức ăn
        
        // 2. Checkout (OrderRequest)
        com.pawconnect.dto.order.OrderRequest orderRequest = new com.pawconnect.dto.order.OrderRequest();
        orderRequest.setBranchId(branchId);

        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        
        // 3. Kiểm tra
        assertNotNull(orderResponse);
        assertEquals("PENDING", orderResponse.getStatus());
        
        // Giá 1 bịch là 200,000 => 2 bịch là 400,000
        assertEquals(0, new BigDecimal("400000").compareTo(orderResponse.getTotalAmount()));
        assertEquals(1, orderResponse.getItems().size());
        assertEquals(2, orderResponse.getItems().get(0).getQuantity());
    }
}
