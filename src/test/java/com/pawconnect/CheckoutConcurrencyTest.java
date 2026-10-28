package com.pawconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pawconnect.dto.order.OrderRequest;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.OrderRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.security.CustomUserDetails;
import com.pawconnect.service.shop.CartService;
import com.pawconnect.service.shop.OrderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:checkout_concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CheckoutConcurrencyTest {
    @Autowired private CartService carts;
    @Autowired private OrderService orders;
    @Autowired private BranchRepository branches;
    @Autowired private ProductRepository products;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void twoCustomersCannotBuyTheLastUnitTwice() throws Exception {
        Branch branch = branches.save(Branch.builder().name("Race branch").address("Race address").build());
        Product product = products.save(Product.builder().name("Last item").price(BigDecimal.TEN)
                .stock(1).branch(branch).build());
        CustomUserDetails first = customer("race-first@test.local");
        CustomUserDetails second = customer("race-second@test.local");
        authenticate(first);
        carts.addItem(product.getId(), null, 1);
        authenticate(second);
        carts.addItem(product.getId(), null, 1);
        SecurityContextHolder.clearContext();

        int successful = race(
                () -> place(first, branch.getId(), "race-first-confirmation-0001"),
                () -> place(second, branch.getId(), "race-second-confirmation-0001"));
        assertEquals(1, successful);
        assertEquals(0, stock(product.getId()));
        assertEquals(1, orderRepository.findByUserId(first.getId()).size()
                + orderRepository.findByUserId(second.getId()).size());
    }

    @Test
    void twoConfirmationsOfTheSameCartCreateOnlyOneOrder() throws Exception {
        Branch branch = branches.save(Branch.builder().name("Retry branch").address("Retry address").build());
        Product product = products.save(Product.builder().name("Retry item").price(BigDecimal.TEN)
                .stock(2).branch(branch).build());
        CustomUserDetails buyer = customer("retry-buyer@test.local");
        authenticate(buyer);
        carts.addItem(product.getId(), null, 1);
        SecurityContextHolder.clearContext();

        int successful = race(
                () -> place(buyer, branch.getId(), "retry-confirmation-000001"),
                () -> place(buyer, branch.getId(), "retry-confirmation-000001"));
        assertEquals(2, successful);
        assertEquals(1, stock(product.getId()));
        assertEquals(1, orderRepository.findByUserId(buyer.getId()).size());
    }

    @Test
    void twoStaffMembersCannotApplyTheSameTransitionTwice() throws Exception {
        Branch branch = branches.save(Branch.builder().name("Staff race branch").address("Staff race address").build());
        Product product = products.save(Product.builder().name("Staff race item").price(BigDecimal.TEN)
                .stock(1).branch(branch).build());
        CustomUserDetails buyer = customer("staff-race-buyer@test.local");
        CustomUserDetails firstStaff = staff("staff-race-first@test.local", branch.getId());
        CustomUserDetails secondStaff = staff("staff-race-second@test.local", branch.getId());
        authenticate(buyer);
        carts.addItem(product.getId(), null, 1);
        place(buyer, branch.getId(), "staff-race-confirmation-01");
        SecurityContextHolder.clearContext();
        Long orderId = orderRepository.findByUserId(buyer.getId()).get(0).getId();
        authenticate(firstStaff);
        orders.confirmPayment(orderId);
        SecurityContextHolder.clearContext();

        int successful = race(
                () -> advance(firstStaff, orderId),
                () -> advance(secondStaff, orderId));
        assertEquals(1, successful);
        assertEquals("SHIPPING", orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_events WHERE order_id = ?", Integer.class, orderId));
    }

    @Test
    void twoStaffMembersCannotConfirmTheSamePaymentTwice() throws Exception {
        Branch branch = branches.save(Branch.builder().name("Payment race branch").address("Payment race address").build());
        Product product = products.save(Product.builder().name("Payment race item").price(BigDecimal.TEN)
                .stock(1).branch(branch).build());
        CustomUserDetails buyer = customer("payment-race-buyer@test.local");
        CustomUserDetails firstStaff = staff("payment-race-first@test.local", branch.getId());
        CustomUserDetails secondStaff = staff("payment-race-second@test.local", branch.getId());
        authenticate(buyer);
        carts.addItem(product.getId(), null, 1);
        place(buyer, branch.getId(), "payment-race-confirm-0001");
        SecurityContextHolder.clearContext();
        Long orderId = orderRepository.findByUserId(buyer.getId()).get(0).getId();

        int successful = race(
                () -> confirmPayment(firstStaff, orderId),
                () -> confirmPayment(secondStaff, orderId));
        assertEquals(1, successful);
        assertEquals("PROCESSING", orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_events WHERE order_id = ?", Integer.class, orderId));
    }

    @Test
    void cancellationRequestAndHandoverCannotBothWin() throws Exception {
        Branch branch = branches.save(Branch.builder().name("Handover race branch").address("Address").build());
        Product product = products.save(Product.builder().name("Handover race item").price(BigDecimal.TEN)
                .stock(1).branch(branch).build());
        CustomUserDetails buyer = customer("handover-race-buyer@test.local");
        CustomUserDetails manager = staff("handover-race-staff@test.local", branch.getId());
        authenticate(buyer);
        carts.addItem(product.getId(), null, 1);
        place(buyer, branch.getId(), "handover-race-confirm-001");
        SecurityContextHolder.clearContext();
        Long orderId = orderRepository.findByUserId(buyer.getId()).get(0).getId();
        authenticate(manager);
        orders.confirmPayment(orderId);
        SecurityContextHolder.clearContext();

        int successful = race(
                () -> requestCancel(buyer, orderId),
                () -> advance(manager, orderId));
        assertEquals(1, successful);
        var order = orderRepository.findById(orderId).orElseThrow();
        assertEquals("PAID", order.getPaymentStatus());
        assertEquals(0, stock(product.getId()));
        if ("SHIPPING".equals(order.getStatus())) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_cancellation_requests WHERE order_id = ?", Integer.class, orderId));
        } else {
            assertEquals("PROCESSING", order.getStatus());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_cancellation_requests WHERE order_id = ? AND status = 'PENDING'", Integer.class, orderId));
        }
    }

    @Test
    void twoStaffMembersCannotApproveTheSameRefundTwice() throws Exception {
        Branch branch = branches.save(Branch.builder().name("Refund race branch").address("Address").build());
        Product product = products.save(Product.builder().name("Refund race item").price(BigDecimal.TEN)
                .stock(1).branch(branch).build());
        CustomUserDetails buyer = customer("refund-race-buyer@test.local");
        CustomUserDetails firstStaff = staff("refund-race-first@test.local", branch.getId());
        CustomUserDetails secondStaff = staff("refund-race-second@test.local", branch.getId());
        authenticate(buyer);
        carts.addItem(product.getId(), null, 1);
        place(buyer, branch.getId(), "refund-race-confirm-0001");
        SecurityContextHolder.clearContext();
        Long orderId = orderRepository.findByUserId(buyer.getId()).get(0).getId();
        authenticate(firstStaff);
        orders.confirmPayment(orderId);
        SecurityContextHolder.clearContext();
        authenticate(buyer);
        orders.requestCancellation(orderId, "Không mua nữa");
        SecurityContextHolder.clearContext();

        int successful = race(
                () -> approveCancel(firstStaff, orderId),
                () -> approveCancel(secondStaff, orderId));
        assertEquals(1, successful);
        assertEquals("CANCELLED", orderRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(1, stock(product.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_cancellation_requests WHERE order_id = ? AND status = 'APPROVED'", Integer.class, orderId));
    }

    private int race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> wrappedFirst = () -> { ready.countDown(); start.await(); return first.call(); };
            Callable<Boolean> wrappedSecond = () -> { ready.countDown(); start.await(); return second.call(); };
            Future<Boolean> one = pool.submit(wrappedFirst);
            Future<Boolean> two = pool.submit(wrappedSecond);
            if (!ready.await(10, TimeUnit.SECONDS)) throw new AssertionError("Workers did not become ready");
            start.countDown();
            return (one.get(20, TimeUnit.SECONDS) ? 1 : 0) + (two.get(20, TimeUnit.SECONDS) ? 1 : 0);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean place(CustomUserDetails buyer, Long branchId, String key) {
        authenticate(buyer);
        try {
            OrderRequest request = new OrderRequest();
            request.setBranchId(branchId);
            request.setIdempotencyKey(key);
            request.setRecipientName("Race Buyer");
            request.setRecipientPhone("0901234567");
            request.setDeliveryAddress("Test address");
            orders.createOrder(request);
            return true;
        } catch (RuntimeException expectedForLosingCheckout) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean advance(CustomUserDetails staff, Long orderId) {
        authenticate(staff);
        try {
            orders.updateOrderStatus(orderId, "SHIPPING", "Bàn giao vận chuyển");
            return true;
        } catch (RuntimeException expectedForLosingTransition) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean confirmPayment(CustomUserDetails staff, Long orderId) {
        authenticate(staff);
        try {
            orders.confirmPayment(orderId);
            return true;
        } catch (RuntimeException expectedForLosingConfirmation) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean requestCancel(CustomUserDetails buyer, Long orderId) {
        authenticate(buyer);
        try {
            orders.requestCancellation(orderId, "Đổi ý");
            return true;
        } catch (RuntimeException expectedForLosingRequest) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean approveCancel(CustomUserDetails staff, Long orderId) {
        authenticate(staff);
        try {
            orders.resolveCancellation(orderId, true, "Chấp thuận");
            return true;
        } catch (RuntimeException expectedForLosingApproval) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private CustomUserDetails customer(String email) {
        User account = users.save(new User("Race Buyer", email, "unused", null,
                roles.findByName(RoleName.CUSTOMER).orElseThrow()));
        return new CustomUserDetails(account.getId(), null, email, "unused",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private CustomUserDetails staff(String email, Long branchId) {
        User account = users.save(new User("Race Staff", email, "unused", null,
                roles.findByName(RoleName.BRANCH_MANAGER).orElseThrow()));
        return new CustomUserDetails(account.getId(), branchId, email, "unused",
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
    }

    private void authenticate(CustomUserDetails buyer) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(buyer, null, buyer.getAuthorities()));
    }

    private int stock(Long productId) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }
}
