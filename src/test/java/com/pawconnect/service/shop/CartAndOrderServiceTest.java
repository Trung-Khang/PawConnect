package com.pawconnect.service.shop;

import com.pawconnect.dto.order.OrderResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;

    private Long productId;
    private Long branchId;

    @BeforeEach
    public void setup() {
        User buyer = userRepository.save(new User("Customer Test", "customer-order@example.test", "unused", null,
                roleRepository.findByName(RoleName.CUSTOMER).orElseThrow()));
        CustomUserDetails principal = new CustomUserDetails(buyer.getId(), null, buyer.getEmail(), "unused",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
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

                .build();
        p = productRepository.save(p);
        productId = p.getId();
    }

    @AfterEach
    public void cleanup() {
        SecurityContextHolder.clearContext();
        // Tránh lỗi foreign key constraint, xóa từ con lên cha nhưng vì Test Transactional, có thể truncate
    }

    @Test
    public void testCartAndCheckoutFlow() {
        // 1. Thêm vào giỏ hàng
        cartService.addItem(productId, null, 2); // Thêm 2 bịch thức ăn
        
        // 2. Checkout (OrderRequest)
        com.pawconnect.dto.order.OrderRequest orderRequest = new com.pawconnect.dto.order.OrderRequest();
        orderRequest.setBranchId(branchId);
        orderRequest.setIdempotencyKey("test-order-confirmation-0001");
        orderRequest.setRecipientName("Customer Test");
        orderRequest.setRecipientPhone("0901234567");
        orderRequest.setDeliveryAddress("123 Test Street");

        OrderResponse orderResponse = orderService.createOrder(orderRequest);
        
        // 3. Kiểm tra
        assertNotNull(orderResponse);
        assertEquals("PAYMENT_REPORTED", orderResponse.getStatus());
        assertEquals("SIMULATED", orderResponse.getPaymentMethod());
        assertEquals("CUSTOMER_CONFIRMED", orderResponse.getPaymentStatus());
        
        // Giá 1 bịch là 200,000 => 2 bịch là 400,000
        assertEquals(0, new BigDecimal("400000").compareTo(orderResponse.getTotalAmount()));
        assertEquals(1, orderResponse.getItems().size());
        assertEquals(2, orderResponse.getItems().get(0).getQuantity());
        assertEquals("Premium Dog Food", orderResponse.getItems().get(0).getProductName());
    }
}
