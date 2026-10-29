package com.pawconnect;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.pawconnect.entity.*;
import com.pawconnect.repository.*;
import com.pawconnect.security.CustomUserDetails;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class CouponCheckoutIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BranchRepository branches;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DiscountCouponRepository coupons;
    @Autowired ServiceTypeRepository serviceTypes;
    @Autowired ServiceBookingRepository bookings;
    @Autowired JdbcTemplate jdbc;
    Branch branch;
    Product product;
    CustomUserDetails customer, other, admin, manager;

    @BeforeEach void prepare() throws Exception {
        branch = branches.save(Branch.builder().name("Coupon HCM").address("Address").build());
        product = products.save(Product.builder().name("Coupon product").branch(branch).price(new BigDecimal("600000")).stock(20).build());
        customer = principal(RoleName.CUSTOMER); other = principal(RoleName.CUSTOMER);
        admin = principal(RoleName.ADMIN); manager = principal(RoleName.BRANCH_MANAGER);
        add(customer);
    }

    @Test void defaultCouponsAndSuggestionUseServerPrices() throws Exception {
        JsonNode quote = quote(customer, null);
        assertEquals(600000, quote.get("subtotal").intValue());
        assertEquals(id("LANDAU15"), quote.get("recommendedCouponId").longValue());
        assertEquals(1, option(quote, "DON500K").get("remaining").intValue());
        assertTrue(option(quote, "DON500K").get("eligible").booleanValue());
        assertEquals(0, option(quote, "SPA50K").get("remaining").intValue());
        assertEquals(510000, quote(customer, id("LANDAU15")).get("totalAmount").intValue());
        assertEquals(540000, quote(customer, id("DON500K")).get("totalAmount").intValue());
        assertEquals(600000, quote(customer, null).get("totalAmount").intValue());
    }

    @Test void thresholdIsStrictlyAbove500000() throws Exception {
        product.setPrice(new BigDecimal("500000")); products.saveAndFlush(product);
        assertFalse(option(quote(customer, null), "DON500K").get("eligible").booleanValue());
        sendQuote(customer, id("DON500K")).andExpect(status().isConflict());
        product.setPrice(new BigDecimal("500001")); products.saveAndFlush(product);
        assertEquals(450001, quote(customer, id("DON500K")).get("totalAmount").intValue());
    }

    @Test void createsDiscountedSnapshotAndIdempotentRetryConsumesOneUse() throws Exception {
        String payload = payload(id("LANDAU15"), "coupon-retry-key-00001");
        JsonNode order = order(customer, payload);
        long orderId = order.get("id").longValue();
        assertEquals(600000, order.get("subtotalAmount").intValue());
        assertEquals(90000, order.get("discountAmount").intValue());
        assertEquals(510000, order.get("totalAmount").intValue());
        assertEquals("LANDAU15", order.get("couponCode").asText());
        assertEquals(orderId, order(customer, payload).get("id").longValue());
        assertEquals(19, stock());
        add(customer);
        assertEquals(0, option(quote(customer, null), "LANDAU15").get("remaining").intValue());
        sendOrder(customer, payload(id("LANDAU15"), "coupon-second-key-00001")).andExpect(status().isConflict());
        assertEquals(19, stock());
        mvc.perform(get("/api/orders/{id}", orderId).with(user(other))).andExpect(status().isForbidden());
        product.setPrice(new BigDecimal("900000")); products.saveAndFlush(product);
        mvc.perform(get("/api/orders/{id}", orderId).with(user(customer)))
                .andExpect(jsonPath("$.discountAmount").value(90000)).andExpect(jsonPath("$.totalAmount").value(510000));
    }

    @Test void directCancellationRestoresUseAndDoesNotAffectOtherCustomer() throws Exception {
        long orderId = order(customer, payload(id("DON500K"), "cancel-coupon-key-0001")).get("id").longValue();
        add(customer); add(other);
        assertEquals(0, option(quote(customer, null), "DON500K").get("remaining").intValue());
        assertEquals(1, option(quote(other, null), "DON500K").get("remaining").intValue());
        mvc.perform(post("/api/orders/{id}/cancel", orderId).param("reason", "Đổi ý").with(user(customer))).andExpect(status().isOk());
        assertEquals(1, option(quote(customer, null), "DON500K").get("remaining").intValue());
        mvc.perform(post("/api/orders/{id}/cancel", orderId).param("reason", "Lặp").with(user(customer))).andExpect(status().isConflict());
        assertEquals(20, stock());
    }

    @Test void approvedRefundRestoresCouponButPendingAndRejectedRequestsDoNot() throws Exception {
        long orderId = order(customer, payload(id("DON500K"), "refund-coupon-key-0001")).get("id").longValue();
        mvc.perform(post("/api/orders/{id}/confirm-payment", orderId).with(user(admin))).andExpect(status().isOk());
        add(customer);
        mvc.perform(post("/api/orders/{id}/cancellation-request", orderId).param("reason", "Đổi ý").with(user(customer))).andExpect(status().isOk());
        assertEquals(0, option(quote(customer, null), "DON500K").get("remaining").intValue());
        mvc.perform(post("/api/orders/{id}/cancellation-request/reject", orderId).param("reason", "Đang xử lý").with(user(admin))).andExpect(status().isOk());
        assertEquals(0, option(quote(customer, null), "DON500K").get("remaining").intValue());
        mvc.perform(post("/api/orders/{id}/cancellation-request", orderId).param("reason", "Cần hủy").with(user(customer))).andExpect(status().isOk());
        mvc.perform(post("/api/orders/{id}/cancellation-request/approve", orderId).param("reason", "Đồng ý").with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.totalAmount").value(540000));
        assertEquals(1, option(quote(customer, null), "DON500K").get("remaining").intValue());
    }

    @Test void spaRequiresCompletedSpaOfCurrentUserAndCannotProduceNegativeTotal() throws Exception {
        ServiceType spa = serviceTypes.save(ServiceType.builder().name("Spa chó").duration(60).price(BigDecimal.TEN).build());
        ServiceBooking booking = bookings.save(ServiceBooking.builder().userId(other.getId()).branch(branch).serviceType(spa)
                .bookingTime(LocalDateTime.now()).status("COMPLETED").build());
        assertFalse(option(quote(customer, null), "SPA50K").get("eligible").booleanValue());
        booking.setUserId(customer.getId()); booking.setStatus("CONFIRMED"); bookings.saveAndFlush(booking);
        assertFalse(option(quote(customer, null), "SPA50K").get("eligible").booleanValue());
        booking.setStatus("COMPLETED"); bookings.saveAndFlush(booking);
        assertTrue(option(quote(customer, null), "SPA50K").get("eligible").booleanValue());
        assertEquals(550000, quote(customer, id("SPA50K")).get("totalAmount").intValue());
        product.setPrice(new BigDecimal("20000")); products.saveAndFlush(product);
        assertEquals(id("SPA50K"), quote(customer, null).get("recommendedCouponId").longValue());
        JsonNode order = order(customer, payload(id("SPA50K"), "spa-capped-key-000001"));
        assertEquals(0, order.get("totalAmount").intValue());
        assertEquals(20000, order.get("discountAmount").intValue());
        add(customer);
        assertEquals(0, option(quote(customer, null), "SPA50K").get("remaining").intValue());
    }

    @Test void firstOrderIsReservedWhilePendingAndRestoredOnCancellation() throws Exception {
        long orderId = order(customer, payload(null, "first-normal-key-00001")).get("id").longValue();
        add(customer);
        assertFalse(option(quote(customer, null), "LANDAU15").get("eligible").booleanValue());
        mvc.perform(post("/api/orders/{id}/cancel", orderId).param("reason", "Hủy").with(user(customer))).andExpect(status().isOk());
        assertTrue(option(quote(customer, null), "LANDAU15").get("eligible").booleanValue());
    }

    @Test void exhaustedAndInactiveCouponsCannotBeForgedAndErrorsRollbackStock() throws Exception {
        long couponId = id("DON500K");
        mvc.perform(patch("/api/admin/coupons/{id}/active", couponId).with(user(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());
        sendOrder(customer, payload(couponId, "inactive-coupon-000001")).andExpect(status().isConflict());
        assertEquals(20, stock());
        sendOrder(customer, payload(999999L, "unknown-coupon-000001")).andExpect(status().isNotFound());
        assertEquals(20, stock());
        sendOrder(customer, payload(id("SPA50K"), "unearned-spa-0000001")).andExpect(status().isConflict());
        assertEquals(20, stock());
    }

    @Test void staleTotalIsRejectedButFakeDiscountFieldsNeverChangeServerAmount() throws Exception {
        String payload = payload(id("DON500K"), "stale-total-key-00001");
        sendOrder(customer, payload.replace("{", "{\"expectedTotalAmount\":1," )).andExpect(status().isConflict());
        assertEquals(20, stock());
        JsonNode result = order(customer, payload.replace("{", "{\"discountAmount\":599999,\"totalAmount\":1,"));
        assertEquals(540000, result.get("totalAmount").intValue());
    }

    @Test void couponManagementIsAdminOnlyAndValidatesInputs() throws Exception {
        mvc.perform(get("/api/admin/coupons")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/coupons").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/coupons").with(user(manager))).andExpect(status().isForbidden());
        mvc.perform(post("/api/coupons/quote").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"branchId\":1}"))
                .andExpect(status().isForbidden());
        String body = "{\"code\":\"NEW20\",\"name\":\"Ưu đãi mới\",\"condition\":\"ORDER_MINIMUM\",\"discountType\":\"PERCENT\",\"discountValue\":20,\"minimumSubtotal\":0,\"usesPerCustomer\":2}";
        mvc.perform(post("/api/admin/coupons").with(user(customer)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/coupons").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/coupons").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/coupons").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(body.replace("NEW20", "NEW101").replace(":20,", ":101,")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/coupons").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(body.replace("NEW20", "bad code")))
                .andExpect(status().isBadRequest());
        assertEquals(2, option(quote(customer, null), "NEW20").get("remaining").intValue());
        order(customer, payload(id("NEW20"), "new20-first-key-00001")); add(customer);
        assertEquals(1, option(quote(customer, null), "NEW20").get("remaining").intValue());
        order(customer, payload(id("NEW20"), "new20-second-key-00001")); add(customer);
        assertEquals(0, option(quote(customer, null), "NEW20").get("remaining").intValue());
    }

    @Test void quoteRejectsForeignCartItemsAndWrongBranch() throws Exception {
        mvc.perform(post("/api/coupons/quote").with(user(customer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"itemIds\":[999999]}" )).andExpect(status().isConflict());
        mvc.perform(post("/api/coupons/quote").with(user(customer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":999999}" )).andExpect(status().isConflict());
        mvc.perform(get("/admin/coupons")).andExpect(status().isOk()).andExpect(view().name("admin/coupons"));
    }

    private long id(String code) { return coupons.findAll().stream().filter(c -> c.getCode().equals(code)).findFirst().orElseThrow().getId(); }
    private JsonNode option(JsonNode quote, String code) { for (JsonNode n : quote.get("coupons")) if (n.get("code").asText().equals(code)) return n; throw new AssertionError(code); }
    private void add(CustomUserDetails who) throws Exception {
        mvc.perform(post("/api/cart/items").with(user(who)).param("productId", product.getId().toString()).param("quantity", "1")).andExpect(status().isOk());
    }
    private ResultActions sendQuote(CustomUserDetails who, Long coupon) throws Exception {
        return mvc.perform(post("/api/coupons/quote").with(user(who)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":" + branch.getId() + ",\"couponId\":" + coupon + "}"));
    }
    private JsonNode quote(CustomUserDetails who, Long coupon) throws Exception { return json.readTree(sendQuote(who, coupon).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    private ResultActions sendOrder(CustomUserDetails who, String payload) throws Exception {
        return mvc.perform(post("/api/orders").with(user(who)).contentType(MediaType.APPLICATION_JSON).content(payload));
    }
    private JsonNode order(CustomUserDetails who, String payload) throws Exception { return json.readTree(sendOrder(who, payload).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    private String payload(Long coupon, String key) { return "{\"branchId\":" + branch.getId() + ",\"couponId\":" + coupon + ",\"idempotencyKey\":\"" + key + "\",\"recipientName\":\"Khách thử\",\"recipientPhone\":\"0901234567\",\"deliveryAddress\":\"Địa chỉ thử\"}"; }
    private int stock() { return jdbc.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, product.getId()); }
    private CustomUserDetails principal(RoleName role) {
        String email = UUID.randomUUID() + "@coupon.test";
        User account = users.save(new User("Coupon buyer", email, "unused", null, roles.findByName(role).orElseThrow()));
        return new CustomUserDetails(account.getId(), role == RoleName.BRANCH_MANAGER ? branch.getId() : null, email, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
