package com.pawconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.BreedType;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.LifeStage;
import com.pawconnect.entity.ListingStatus;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.PuppyListing;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.OrderRepository;
import com.pawconnect.repository.OrderStatusEventRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.PuppyListingRepository;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.security.CustomUserDetails;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CheckoutReceiptIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BranchRepository branches;
    @Autowired private ProductRepository products;
    @Autowired private PuppyListingRepository puppyListings;
    @Autowired private CategoryRepository categories;
    @Autowired private RoleRepository roles;
    @Autowired private UserRepository users;
    @Autowired private OrderRepository orders;
    @Autowired private OrderStatusEventRepository events;

    private Branch hcm;
    private Branch hn;
    private Product product;
    private CustomUserDetails customer;
    private CustomUserDetails anotherCustomer;
    private CustomUserDetails hcmManager;
    private CustomUserDetails hnManager;
    private CustomUserDetails admin;

    @BeforeEach
    void prepare() {
        hcm = branches.save(Branch.builder().name("Chi nhánh HCM").address("123 HCM").build());
        hn = branches.save(Branch.builder().name("Chi nhánh Hà Nội").address("456 Hà Nội").build());
        product = products.save(Product.builder().name("Thức ăn A").price(new BigDecimal("120000"))
                .stock(5).branch(hcm).build());
        customer = principal("customer-checkout@test.local", RoleName.CUSTOMER, null);
        anotherCustomer = principal("other-checkout@test.local", RoleName.CUSTOMER, null);
        hcmManager = principal("hcm-checkout@test.local", RoleName.BRANCH_MANAGER, hcm.getId());
        hnManager = principal("hn-checkout@test.local", RoleName.BRANCH_MANAGER, hn.getId());
        admin = principal("admin-checkout@test.local", RoleName.ADMIN, null);
    }

    @Test
    void purchasePagesRenderWithoutExposingOrderDataInHtml() throws Exception {
        mvc.perform(get("/checkout")).andExpect(status().isOk()).andExpect(view().name("shop/checkout"));
        mvc.perform(get("/orders")).andExpect(status().isOk()).andExpect(view().name("shop/my-orders"));
        mvc.perform(get("/orders/123/receipt")).andExpect(status().isOk()).andExpect(view().name("shop/receipt"));
    }

    @Test
    void checkoutRecordsCustomerConfirmationAndRetryDoesNotCreateSecondOrder() throws Exception {
        addProduct(customer, product.getId(), 2);
        String payload = orderPayload(hcm.getId(), "checkout-repeat-000001");
        long id = createOrder(customer, payload);

        mvc.perform(get("/api/orders/{id}", id).with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_REPORTED"))
                .andExpect(jsonPath("$.paymentMethod").value("SIMULATED"))
                .andExpect(jsonPath("$.paymentStatus").value("CUSTOMER_CONFIRMED"))
                .andExpect(jsonPath("$.customerName").value("Test Customer"))
                .andExpect(jsonPath("$.branchName").value("Chi nhánh HCM"))
                .andExpect(jsonPath("$.recipientPhone").value("0901234567"))
                .andExpect(jsonPath("$.deliveryAddress").value("123 Test Street"))
                .andExpect(jsonPath("$.items[0].productName").value("Thức ăn A"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.statusHistory.length()").value(1))
                .andExpect(jsonPath("$.statusHistory[0].status").value("PAYMENT_REPORTED"))
                .andExpect(jsonPath("$.statusHistory[0].createdAt").exists())
                .andExpect(jsonPath("$.totalAmount").value(240000));
        assertEquals(id, createOrder(customer, payload));
        assertEquals(1, orders.findByUserId(customer.getId()).size());
        assertEquals(3, stock(product.getId()));
        assertEquals(1, events.count());
        mvc.perform(get("/api/cart").with(user(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));

        product.setName("Tên mới");
        product.setPrice(new BigDecimal("999000"));
        products.saveAndFlush(product);
        hcm.setName("Tên chi nhánh mới");
        branches.saveAndFlush(hcm);
        mvc.perform(get("/api/orders/{id}", id).with(user(customer)))
                .andExpect(jsonPath("$.items[0].productName").value("Thức ăn A"))
                .andExpect(jsonPath("$.items[0].price").value(120000))
                .andExpect(jsonPath("$.branchName").value("Chi nhánh HCM"));
    }

    @Test
    void serverDerivesBuyerAndPriceInsteadOfTrustingClientFields() throws Exception {
        addProduct(customer, product.getId(), 1);
        String payload = orderPayload(hcm.getId(), "ignore-client-price-001")
                .replace("\"branchId\"", "\"userId\":999999,\"totalAmount\":1,\"branchId\"");
        mvc.perform(post("/api/orders").with(user(customer)).contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(customer.getId()))
                .andExpect(jsonPath("$.totalAmount").value(120000));
    }

    @Test
    void invalidCartQuantityAndMissingDeliveryDataAreRejected() throws Exception {
        mvc.perform(post("/api/cart/items").with(user(customer))
                        .param("productId", product.getId().toString()).param("quantity", "-1"))
                .andExpect(status().isConflict());
        addProduct(customer, product.getId(), 1);
        mvc.perform(post("/api/orders").with(user(customer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":" + hcm.getId() + ",\"idempotencyKey\":\"missing-details-0001\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(5, stock(product.getId()));
    }

    @Test
    void branchMismatchAndMixedCartAreRejectedWithoutStockChange() throws Exception {
        addProduct(customer, product.getId(), 1);
        mvc.perform(post("/api/orders").with(user(customer)).contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(hn.getId(), "wrong-branch-000001")))
                .andExpect(status().isConflict());
        assertEquals(5, stock(product.getId()));
        assertEquals(0, orders.findByUserId(customer.getId()).size());

        Product other = products.save(Product.builder().name("Sản phẩm HN").price(BigDecimal.TEN)
                .stock(5).branch(hn).build());
        mvc.perform(post("/api/cart/items").with(user(customer))
                        .param("productId", other.getId().toString()).param("quantity", "1"))
                .andExpect(status().isConflict());
    }

    @Test
    void receiptAndCancellationAreScopedToOwnerAndBranch() throws Exception {
        addProduct(customer, product.getId(), 1);
        long id = createOrder(customer, orderPayload(hcm.getId(), "receipt-scope-00001"));

        mvc.perform(get("/api/orders/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/{id}", id).with(user(anotherCustomer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/{id}", id).with(user(hnManager))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/{id}", id).with(user(hcmManager))).andExpect(status().isOk());
        mvc.perform(get("/api/orders/{id}", id).with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/orders/my").with(user(anotherCustomer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/orders/{id}/cancel", id).param("reason", "Không cần nữa")
                .with(user(anotherCustomer))).andExpect(status().isForbidden());
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "CONFIRMED").with(user(hnManager)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/{id}/confirm-payment", id).with(user(hnManager)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/{id}/confirm-payment", id).with(user(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/{id}/confirm-payment", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffPaymentConfirmationAlsoAcceptsOrderForProcessing() throws Exception {
        addProduct(customer, product.getId(), 1);
        long id = createOrder(customer, orderPayload(hcm.getId(), "payment-two-party-001"));
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "CONFIRMED").with(user(hcmManager)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/confirm-payment", id).with(user(hcmManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.statusHistory[1].status").value("PROCESSING"))
                .andExpect(jsonPath("$.statusHistory[1].createdAt").exists());
        assertEquals(hcmManager.getId(), events.findByOrder_IdOrderByCreatedAtAscIdAsc(id).get(1).getActorUserId());
        mvc.perform(post("/api/orders/{id}/confirm-payment", id).with(user(hcmManager)))
                .andExpect(status().isConflict());
        assertEquals(2, events.count());
        assertEquals(4, stock(product.getId()));
    }

    @Test
    void cancellationBeforeStaffPaymentConfirmationDoesNotInventRefund() throws Exception {
        addProduct(customer, product.getId(), 2);
        long id = createOrder(customer, orderPayload(hcm.getId(), "cancel-reported-00001"));
        mvc.perform(post("/api/orders/{id}/cancel", id).with(user(customer)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancel", id).param("reason", "   ")
                        .with(user(customer)))
                .andExpect(status().isConflict());
        assertEquals(3, stock(product.getId()));
        mvc.perform(post("/api/orders/{id}/cancel", id).param("reason", "Đổi ý mua hàng")
                        .with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.statusHistory[1].reason").value("Đổi ý mua hàng"));
        assertEquals(5, stock(product.getId()));
        mvc.perform(post("/api/orders/{id}/confirm-payment", id).with(user(hcmManager)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancel", id).param("reason", "Hủy lần hai")
                        .with(user(customer)))
                .andExpect(status().isConflict());
        assertEquals(5, stock(product.getId()));
    }

    @Test
    void staffCancellationNeedsReasonAndAdminCanConfirmPayment() throws Exception {
        addProduct(customer, product.getId(), 1);
        long cancelledId = createOrder(customer, orderPayload(hcm.getId(), "staff-cancel-reported-01"));
        mvc.perform(put("/api/orders/{id}/status", cancelledId).param("status", "CANCELLED")
                        .with(user(hcmManager)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/orders/{id}/status", cancelledId).param("status", "CANCELLED")
                        .param("reason", "Khách đề nghị hủy").with(user(hnManager)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/orders/{id}/status", cancelledId).param("status", "CANCELLED")
                        .param("reason", "Khách đề nghị hủy").with(user(hcmManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.statusHistory[1].reason").value("Khách đề nghị hủy"));
        assertEquals(5, stock(product.getId()));

        addProduct(customer, product.getId(), 1);
        long paidId = createOrder(customer, orderPayload(hcm.getId(), "admin-confirm-payment-1"));
        mvc.perform(post("/api/orders/{id}/confirm-payment", paidId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
        mvc.perform(put("/api/orders/{id}/status", paidId).param("status", "CANCELLED")
                        .param("reason", "Hủy trước khi tiếp nhận").with(user(hcmManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        assertEquals(5, stock(product.getId()));
    }

    @Test
    void cancellationRequestApprovalRefundsOnceAndRestoresStock() throws Exception {
        addProduct(customer, product.getId(), 2);
        long id = createOrder(customer, orderPayload(hcm.getId(), "cancel-before-000001"));
        confirmPayment(id, hcmManager);
        mvc.perform(post("/api/orders/{id}/cancel", id).param("reason", "Không cần nữa")
                        .with(user(customer))).andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Không cần nữa")
                        .with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.cancellationRequests[0].status").value("PENDING"));
        assertEquals(3, stock(product.getId()));
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "SHIPPING").with(user(hcmManager)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancellation-request/approve", id)
                        .param("reason", "Chấp thuận hủy trước khi giao").with(user(hcmManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.cancellationRequests[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.cancellationRequests[0].decisionReason").value("Chấp thuận hủy trước khi giao"))
                .andExpect(jsonPath("$.statusHistory.length()").value(3))
                .andExpect(jsonPath("$.statusHistory[2].status").value("CANCELLED"));
        assertEquals(5, stock(product.getId()));
        assertEquals(3, events.count());
        mvc.perform(post("/api/orders/{id}/cancel", id).param("reason", "Hủy lần hai")
                .with(user(customer))).andExpect(status().isConflict());
        assertEquals(5, stock(product.getId()));
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "SHIPPING").with(user(hcmManager)))
                .andExpect(status().isConflict());
    }

    @Test
    void puppyPurchaseCreatesReceiptAndCancellationRestoresAvailability() throws Exception {
        Category category = categories.save(Category.builder().name("Thú cưng").build());
        PuppyListing puppy = puppyListings.save(PuppyListing.builder()
                .listingTitle("Cún Poodle").branch(hcm).category(category)
                .breedCode("POODLE").breedType(BreedType.PUREBRED).lifeStage(LifeStage.PUPPY)
                .ageMonths(3).pricePerPuppyVnd(new BigDecimal("3500000"))
                .stock(1).status(ListingStatus.AVAILABLE).build());
        mvc.perform(post("/api/cart/items").with(user(customer))
                        .param("puppyListingId", puppy.getId().toString()).param("quantity", "1"))
                .andExpect(status().isOk());
        long id = createOrder(customer, orderPayload(hcm.getId(), "puppy-purchase-00001"));
        mvc.perform(get("/api/orders/{id}", id).with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Cún Poodle"))
                .andExpect(jsonPath("$.items[0].puppyListingId").value(puppy.getId()))
                .andExpect(jsonPath("$.totalAmount").value(3500000));
        assertEquals(0, jdbc.queryForObject("SELECT stock FROM puppy_listings WHERE id = ?", Integer.class, puppy.getId()));
        assertEquals("SOLD_OUT", jdbc.queryForObject("SELECT status FROM puppy_listings WHERE id = ?", String.class, puppy.getId()));
        confirmPayment(id, hcmManager);
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Thay đổi kế hoạch")
                        .with(user(customer))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/{id}/cancellation-request/approve", id)
                        .param("reason", "Đồng ý trước khi bàn giao").with(user(hcmManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        assertEquals(1, jdbc.queryForObject("SELECT stock FROM puppy_listings WHERE id = ?", Integer.class, puppy.getId()));
        assertEquals("AVAILABLE", jdbc.queryForObject("SELECT status FROM puppy_listings WHERE id = ?", String.class, puppy.getId()));
    }

    @Test
    void staffMustFollowTransitionsAndResolveRequestsBeforeShipping() throws Exception {
        addProduct(customer, product.getId(), 1);
        long id = createOrder(customer, orderPayload(hcm.getId(), "status-flow-000001"));
        confirmPayment(id, hcmManager);
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "CONFIRMED").with(user(hcmManager)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancel", id).param("reason", "Đã giao trễ")
                .with(user(customer))).andExpect(status().isConflict());
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "PREPARING").with(user(hcmManager)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Đổi ý")
                .with(user(customer))).andExpect(status().isOk());
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "SHIPPING").with(user(hcmManager)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancellation-request/reject", id)
                .param("reason", "Đã chuẩn bị hàng").with(user(hcmManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequests[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "SHIPPING").with(user(hcmManager)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Đổi ý nữa")
                .with(user(customer))).andExpect(status().isConflict());
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "COMPLETED").with(user(hcmManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory.length()").value(4))
                .andExpect(jsonPath("$.statusHistory[3].status").value("COMPLETED"));
        assertEquals(4, events.count());
    }

    @Test
    void cancellationRequestRequiresReasonOwnerBranchAndSinglePendingRequest() throws Exception {
        addProduct(customer, product.getId(), 1);
        long id = createOrder(customer, orderPayload(hcm.getId(), "request-guards-000001"));
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Đổi ý")
                .with(user(customer))).andExpect(status().isConflict());
        confirmPayment(id, hcmManager);
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).with(user(customer)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "   ")
                .with(user(customer))).andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Không còn nhu cầu")
                .with(user(anotherCustomer))).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Không còn nhu cầu")
                .with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequests[0].reason").value("Không còn nhu cầu"))
                .andExpect(jsonPath("$.cancellationRequests[0].requestedAt").exists());
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Gửi lại")
                .with(user(customer))).andExpect(status().isConflict());
        mvc.perform(put("/api/orders/{id}/status", id).param("status", "CANCELLED")
                .param("reason", "Hủy thay").with(user(hcmManager))).andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancellation-request/approve", id)
                .param("reason", "Đồng ý").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/{id}/cancellation-request/approve", id)
                .param("reason", "Đồng ý").with(user(hnManager))).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/{id}/cancellation-request/approve", id)
                .with(user(hcmManager))).andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancellation-request/reject", id)
                .param("reason", "   ").with(user(hcmManager))).andExpect(status().isConflict());
        assertEquals(4, stock(product.getId()));
        mvc.perform(get("/api/orders/{id}", id).with(user(customer)))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.cancellationRequests[0].status").value("PENDING"));
    }

    @Test
    void rejectedRequestKeepsOrderAndAllowsNewRequestBeforeHandover() throws Exception {
        addProduct(customer, product.getId(), 1);
        long id = createOrder(customer, orderPayload(hcm.getId(), "request-rejected-00001"));
        confirmPayment(id, hcmManager);
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Đổi lịch")
                .with(user(customer))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/{id}/cancellation-request/reject", id)
                .param("reason", "Chi nhánh có thể giao đúng lịch").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.cancellationRequests[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.cancellationRequests[0].decisionReason").value("Chi nhánh có thể giao đúng lịch"))
                .andExpect(jsonPath("$.cancellationRequests[0].resolvedAt").exists());
        assertEquals(4, stock(product.getId()));
        mvc.perform(post("/api/orders/{id}/cancellation-request/reject", id)
                .param("reason", "Lặp").with(user(hcmManager))).andExpect(status().isConflict());
        mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Vẫn muốn hủy")
                .with(user(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequests.length()").value(2))
                .andExpect(jsonPath("$.cancellationRequests[1].status").value("PENDING"));
        mvc.perform(post("/api/orders/{id}/cancellation-request/approve", id)
                .param("reason", "Đồng ý hủy").with(user(hcmManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationRequests[1].status").value("APPROVED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        assertEquals(5, stock(product.getId()));
    }

    @Test
    void staffCanCancelProcessingBeforeHandoverButNotAfterShipping() throws Exception {
        addProduct(customer, product.getId(), 1);
        long cancelId = createOrder(customer, orderPayload(hcm.getId(), "staff-processing-cancel1"));
        confirmPayment(cancelId, hcmManager);
        mvc.perform(put("/api/orders/{id}/status", cancelId).param("status", "CANCELLED")
                .param("reason", "Không thể giao hàng").with(user(hcmManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        assertEquals(5, stock(product.getId()));

        addProduct(customer, product.getId(), 1);
        long shippedId = createOrder(customer, orderPayload(hcm.getId(), "shipped-no-cancel-0001"));
        confirmPayment(shippedId, hcmManager);
        mvc.perform(put("/api/orders/{id}/status", shippedId).param("status", "SHIPPING")
                .with(user(hcmManager))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/{id}/cancellation-request", shippedId)
                .param("reason", "Đổi ý").with(user(customer))).andExpect(status().isConflict());
        mvc.perform(put("/api/orders/{id}/status", shippedId).param("status", "CANCELLED")
                .param("reason", "Hủy trễ").with(user(hcmManager))).andExpect(status().isConflict());
        assertEquals(4, stock(product.getId()));
    }

    @Test
    void preexistingProcessingStatusesCanUseTheNewCancellationFlow() throws Exception {
        for (String legacyStatus : List.of("PAID", "CONFIRMED", "PREPARING")) {
            addProduct(customer, product.getId(), 1);
            long id = createOrder(customer, orderPayload(hcm.getId(), "legacy-order-" + legacyStatus.toLowerCase() + "-0001"));
            confirmPayment(id, hcmManager);
            var order = orders.findById(id).orElseThrow();
            order.setStatus(legacyStatus);
            orders.saveAndFlush(order);
            mvc.perform(post("/api/orders/{id}/cancellation-request", id).param("reason", "Đổi ý")
                    .with(user(customer))).andExpect(status().isOk());
            mvc.perform(post("/api/orders/{id}/cancellation-request/approve", id)
                    .param("reason", "Đồng ý").with(user(hcmManager)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
            assertEquals(5, stock(product.getId()));
        }
    }

    private void confirmPayment(long id, CustomUserDetails staff) throws Exception {
        mvc.perform(post("/api/orders/{id}/confirm-payment", id).with(user(staff)))
                .andExpect(status().isOk());
    }

    private CustomUserDetails principal(String email, RoleName role, Long branchId) {
        User account = users.save(new User("Test Customer", email, "unused", null,
                roles.findByName(role).orElseThrow()));
        return new CustomUserDetails(account.getId(), branchId, email, "unused",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private void addProduct(CustomUserDetails buyer, Long id, int quantity) throws Exception {
        mvc.perform(post("/api/cart/items").with(user(buyer))
                        .param("productId", id.toString()).param("quantity", String.valueOf(quantity)))
                .andExpect(status().isOk());
    }

    private long createOrder(CustomUserDetails buyer, String payload) throws Exception {
        String body = mvc.perform(post("/api/orders").with(user(buyer)).contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(body);
        return response.get("id").asLong();
    }

    private String orderPayload(Long branchId, String key) {
        return """
                {"branchId":%d,"idempotencyKey":"%s","recipientName":"Người nhận",
                 "recipientPhone":"0901234567","deliveryAddress":"123 Test Street"}
                """.formatted(branchId, key);
    }

    private int stock(Long productId) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }
}
