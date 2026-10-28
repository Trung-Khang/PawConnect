package com.pawconnect.service.shop;

import com.pawconnect.dto.order.OrderRequest;
import com.pawconnect.dto.order.OrderResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Cart;
import com.pawconnect.entity.CartItem;
import com.pawconnect.entity.ListingStatus;
import com.pawconnect.entity.Order;
import com.pawconnect.entity.OrderCancellationRequest;
import com.pawconnect.entity.OrderItem;
import com.pawconnect.entity.OrderStatusEvent;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.PuppyListing;
import com.pawconnect.entity.User;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CartRepository;
import com.pawconnect.repository.OrderRepository;
import com.pawconnect.repository.OrderCancellationRequestRepository;
import com.pawconnect.repository.OrderStatusEventRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.PuppyListingRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.security.SecurityUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String PAYMENT_REPORTED = "PAYMENT_REPORTED";
    private static final String CUSTOMER_CONFIRMED = "CUSTOMER_CONFIRMED";
    private static final String PAID = "PAID";
    private static final String PROCESSING = "PROCESSING";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String REFUNDED = "REFUNDED";
    private static final String CANCELLED = "CANCELLED";
    private static final Map<String, String> NEXT_STATUS = Map.of(
            PROCESSING, "SHIPPING",
            PAID, "SHIPPING",
            "CONFIRMED", "SHIPPING",
            "PREPARING", "SHIPPING",
            "SHIPPING", "COMPLETED");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PuppyListingRepository puppyListingRepository;
    private final BranchRepository branchRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final OrderStatusEventRepository statusEventRepository;
    private final OrderCancellationRequestRepository cancellationRequestRepository;
    private final CartService cartService;
    private final com.pawconnect.service.notification.NotificationService notificationService;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!SecurityUtils.hasRole("CUSTOMER")) throw new AccessDeniedException("Chỉ khách hàng được đặt mua.");
        if (request == null || request.getIdempotencyKey() == null
                || !request.getIdempotencyKey().matches("[A-Za-z0-9-]{16,64}")) {
            throw new BusinessException("Mã xác nhận đơn không hợp lệ.");
        }
        if (request.getBranchId() == null || request.getRecipientName() == null
                || request.getRecipientName().isBlank() || request.getRecipientName().length() > 120
                || request.getRecipientPhone() == null || !request.getRecipientPhone().matches("0[35789][0-9]{8}")
                || request.getDeliveryAddress() == null || request.getDeliveryAddress().isBlank()
                || request.getDeliveryAddress().length() > 500) {
            throw new BusinessException("Thông tin nhận hàng không hợp lệ.");
        }

        // A retry of the same confirmation always resolves to the first order.
        Order previous = orderRepository.findByUserIdAndIdempotencyKey(userId, request.getIdempotencyKey()).orElse(null);
        if (previous != null) return mapToResponse(previous);

        // Lock the customer's cart so two different confirmation keys cannot consume it twice.
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException("Giỏ hàng đang trống."));
        previous = orderRepository.findByUserIdAndIdempotencyKey(userId, request.getIdempotencyKey()).orElse(null);
        if (previous != null) return mapToResponse(previous);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessException("Giỏ hàng đang trống.");
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh."));
        User buyer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản khách hàng."));
        Order order = Order.builder()
                .userId(userId)
                .branch(branch)
                .status(PAYMENT_REPORTED)
                .paymentMethod("SIMULATED")
                .paymentStatus(CUSTOMER_CONFIRMED)
                .idempotencyKey(request.getIdempotencyKey())
                .customerName(buyer.getFullName())
                .branchNameSnapshot(branch.getName())
                .branchAddressSnapshot(branch.getAddress())
                .branchPhoneSnapshot(branch.getPhone())
                .recipientName(request.getRecipientName().trim())
                .recipientPhone(request.getRecipientPhone().trim())
                .deliveryAddress(request.getDeliveryAddress().trim())
                .orderItems(new ArrayList<>())
                .build();

        List<CartItem> itemsToOrder = cart.getItems();
        if (request.getItemIds() != null && !request.getItemIds().isEmpty()) {
            itemsToOrder = cart.getItems().stream()
                    .filter(item -> request.getItemIds().contains(item.getId()))
                    .toList();
            if (itemsToOrder.isEmpty()) {
                throw new BusinessException("Không tìm thấy sản phẩm đã chọn trong giỏ hàng.");
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : itemsToOrder) {
            if (cartItem.getQuantity() == null || cartItem.getQuantity() < 1
                    || (cartItem.getProduct() == null) == (cartItem.getPuppyListing() == null)) {
                throw new BusinessException("Giỏ hàng chứa sản phẩm không hợp lệ.");
            }
            Product product = cartItem.getProduct();
            PuppyListing puppy = cartItem.getPuppyListing();
            Long itemBranchId = product != null ? product.getBranch().getId() : puppy.getBranch().getId();
            if (!Objects.equals(itemBranchId, branch.getId())) {
                throw new BusinessException("Đơn hàng chỉ được chứa sản phẩm của chi nhánh đã chọn.");
            }
            BigDecimal price = product != null ? product.getPrice() : puppy.getPricePerPuppyVnd();
            if (price == null || price.signum() < 0) throw new BusinessException("Giá sản phẩm không hợp lệ.");
            int updated = product != null
                    ? productRepository.decreaseStockBy(product.getId(), cartItem.getQuantity())
                    : puppyListingRepository.decrementStock(puppy.getId(), cartItem.getQuantity());
            if (updated != 1) throw new BusinessException("Sản phẩm đã hết hàng hoặc vừa thay đổi tồn kho.");

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .puppyListing(puppy)
                    .itemNameSnapshot(product != null ? product.getName() : puppy.getListingTitle())
                    .quantity(cartItem.getQuantity())
                    .price(price)
                    .build();
            order.getOrderItems().add(item);
            total = total.add(price.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }
        order.setTotalAmount(total);
        orderRepository.saveAndFlush(order);
        recordEvent(order, null, PAYMENT_REPORTED, userId, "Khách xác nhận thanh toán");
        if (request.getItemIds() != null && !request.getItemIds().isEmpty()) {
            cartService.removeItems(cart, itemsToOrder);
        } else {
            cartService.clearCart(cart);
        }

        try {
            if (notificationService != null && buyer != null) {
                String amountStr = String.format("%,d", total.longValue());
                // Gửi thông báo đến chuông của khách hàng
                notificationService.createNotification(
                        buyer,
                        "Đơn hàng #" + order.getId() + " thanh toán thành công 🎉",
                        "Đơn hàng #" + order.getId() + " (trị giá " + amountStr + " VND) đã được xác nhận. Chi nhánh sẽ sớm đóng gói và vận chuyển đến bạn.",
                        "ORDER_CREATED",
                        "/orders",
                        order.getId()
                );
                // Gửi thông báo đến chuông của Quản lý chi nhánh
                notificationService.notifyBranchManagers(
                        branch.getId(),
                        "Đơn hàng mới #" + order.getId() + " tại chi nhánh",
                        "Khách hàng " + buyer.getFullName() + " vừa đặt đơn hàng #" + order.getId() + " trị giá " + amountStr + " VND. Hãy kiểm tra và xử lý đơn.",
                        "ORDER_CREATED",
                        "/admin/orders",
                        order.getId()
                );
            }
        } catch (Exception ignored) {
        }

        return mapToResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(SecurityUtils.getCurrentUserId())
                .stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getManagedOrders() {
        List<Order> orders = SecurityUtils.hasRole("BRANCH_MANAGER")
                ? orderRepository.findByBranchIdOrderByCreatedAtDesc(SecurityUtils.getCurrentUserBranchId())
                : orderRepository.findAllByOrderByCreatedAtDesc();
        return orders.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        requireAccess(order);
        return mapToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelMyOrder(Long orderId, String reason) {
        if (!SecurityUtils.hasRole("CUSTOMER")) {
            throw new AccessDeniedException("Chỉ khách hàng được hủy đơn của mình.");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        if (!Objects.equals(order.getUserId(), SecurityUtils.getCurrentUserId())) {
            throw new AccessDeniedException("Khách chỉ được hủy đơn của chính mình.");
        }
        if (!PAYMENT_REPORTED.equals(order.getStatus())) {
            throw new BusinessException(isProcessing(order)
                    ? "Đơn đang xử lý: hãy gửi yêu cầu hủy để chi nhánh xem xét."
                    : "Không thể hủy trực tiếp đơn đã bàn giao vận chuyển hoặc đã kết thúc.");
        }
        return cancelEligibleOrder(order, requiredReason(reason, "Cần ghi lý do hủy đơn."), false);
    }

    @Override
    @Transactional
    public OrderResponse requestCancellation(Long orderId, String reason) {
        if (!SecurityUtils.hasRole("CUSTOMER")) {
            throw new AccessDeniedException("Chỉ khách hàng được yêu cầu hủy đơn của mình.");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        if (!Objects.equals(order.getUserId(), SecurityUtils.getCurrentUserId())) {
            throw new AccessDeniedException("Khách chỉ được yêu cầu hủy đơn của chính mình.");
        }
        if (!isProcessing(order) || !PAID.equals(order.getPaymentStatus())) {
            throw new BusinessException("Chỉ được yêu cầu hủy khi đơn đang xử lý và chưa bàn giao vận chuyển.");
        }
        if (pendingCancellation(order).isPresent()) {
            throw new BusinessException("Đơn đã có yêu cầu hủy đang chờ chi nhánh trả lời.");
        }
        OrderCancellationRequest request = new OrderCancellationRequest();
        request.setOrder(order);
        request.setRequestedBy(SecurityUtils.getCurrentUserId());
        request.setReason(requiredReason(reason, "Cần ghi lý do yêu cầu hủy."));
        request.setStatus(PENDING);
        cancellationRequestRepository.save(request);
        return mapToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse resolveCancellation(Long orderId, boolean approve, String reason) {
        requireStaffRole();
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        requireStaffBranchAccess(order);
        if (!isProcessing(order) || !PAID.equals(order.getPaymentStatus())) {
            throw new BusinessException("Đơn không còn ở giai đoạn có thể duyệt hủy.");
        }
        OrderCancellationRequest request = pendingCancellation(order)
                .orElseThrow(() -> new BusinessException("Không có yêu cầu hủy đang chờ duyệt."));
        String decisionReason = requiredReason(reason, "Cần ghi lý do chấp nhận hoặc từ chối yêu cầu hủy.");
        request.setStatus(approve ? APPROVED : REJECTED);
        request.setDecisionReason(decisionReason);
        request.setResolvedBy(SecurityUtils.getCurrentUserId());
        request.setResolvedAt(Instant.now());
        cancellationRequestRepository.save(request);
        if (approve) return cancelEligibleOrder(order, decisionReason, true);
        return mapToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse confirmPayment(Long orderId) {
        if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasRole("BRANCH_MANAGER")) {
            throw new AccessDeniedException("Chỉ nhân viên được xác nhận đã nhận thanh toán.");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        requireStaffBranchAccess(order);
        if (!PAYMENT_REPORTED.equals(order.getStatus())
                || !CUSTOMER_CONFIRMED.equals(order.getPaymentStatus())) {
            throw new BusinessException("Đơn không còn chờ xác nhận thanh toán.");
        }
        order.setStatus(PROCESSING);
        order.setPaymentStatus(PAID);
        orderRepository.save(order);
        recordEvent(order, PAYMENT_REPORTED, PROCESSING, SecurityUtils.getCurrentUserId(),
                "Chi nhánh xác nhận đã nhận thanh toán và tiếp nhận xử lý đơn");
        return mapToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String status, String reason) {
        requireStaffRole();
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        requireStaffBranchAccess(order);
        if (CANCELLED.equals(status)) {
            return cancelEligibleOrder(order, requiredReason(reason, "Cần ghi lý do hủy đơn."), false);
        }
        if (!Objects.equals(NEXT_STATUS.get(order.getStatus()), status)) {
            throw new BusinessException("Không thể chuyển trạng thái đơn từ " + order.getStatus() + " sang " + status + ".");
        }
        if (pendingCancellation(order).isPresent()) {
            throw new BusinessException("Phải duyệt hoặc từ chối yêu cầu hủy trước khi bàn giao vận chuyển.");
        }
        String before = order.getStatus();
        order.setStatus(status);
        orderRepository.save(order);
        recordEvent(order, before, status, SecurityUtils.getCurrentUserId(),
                reason == null || reason.isBlank() ? "Chi nhánh cập nhật trạng thái đơn" : reason.trim());
        return mapToResponse(order);
    }

    private OrderResponse cancelEligibleOrder(Order order, String reason, boolean approvingRequest) {
        boolean received = isProcessing(order) && PAID.equals(order.getPaymentStatus());
        boolean reported = PAYMENT_REPORTED.equals(order.getStatus())
                && CUSTOMER_CONFIRMED.equals(order.getPaymentStatus());
        if (!received && !reported) {
            throw new BusinessException("Chỉ có thể hủy trước khi đơn bàn giao vận chuyển.");
        }
        if (!approvingRequest && pendingCancellation(order).isPresent()) {
            throw new BusinessException("Phải duyệt hoặc từ chối yêu cầu hủy đang chờ trước khi hủy đơn.");
        }
        for (OrderItem item : order.getOrderItems()) {
            int updated = item.getProduct() != null
                    ? productRepository.restoreStock(item.getProduct().getId(), item.getQuantity())
                    : puppyListingRepository.restoreStock(item.getPuppyListing().getId(), item.getQuantity());
            if (updated != 1) throw new BusinessException("Không thể khôi phục tồn kho để hủy đơn.");
        }
        String before = order.getStatus();
        order.setStatus(CANCELLED);
        order.setPaymentStatus(received ? REFUNDED : CANCELLED);
        orderRepository.save(order);
        recordEvent(order, before, CANCELLED, SecurityUtils.getCurrentUserId(), reason);
        return mapToResponse(order);
    }

    private boolean isProcessing(Order order) {
        return List.of(PROCESSING, PAID, "CONFIRMED", "PREPARING").contains(order.getStatus());
    }

    private java.util.Optional<OrderCancellationRequest> pendingCancellation(Order order) {
        return cancellationRequestRepository.findFirstByOrder_IdAndStatusOrderByRequestedAtDescIdDesc(order.getId(), PENDING);
    }

    private String requiredReason(String reason, String error) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException(error);
        }
        return reason.trim();
    }

    private void requireStaffRole() {
        if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasRole("BRANCH_MANAGER")) {
            throw new AccessDeniedException("Chỉ nhân viên được xử lý đơn hàng.");
        }
    }

    private void requireStaffBranchAccess(Order order) {
        if (SecurityUtils.hasRole("BRANCH_MANAGER")
                && !Objects.equals(SecurityUtils.getCurrentUserBranchId(), order.getBranch().getId())) {
            throw new AccessDeniedException("Nhân viên chỉ được xử lý đơn của chi nhánh mình.");
        }
    }

    private void requireAccess(Order order) {
        if (SecurityUtils.hasRole("ADMIN")) return;
        if (SecurityUtils.hasRole("BRANCH_MANAGER")
                && Objects.equals(SecurityUtils.getCurrentUserBranchId(), order.getBranch().getId())) return;
        if (SecurityUtils.hasRole("CUSTOMER")
                && Objects.equals(SecurityUtils.getCurrentUserId(), order.getUserId())) return;
        throw new AccessDeniedException("Không có quyền xem đơn hàng này.");
    }

    private void recordEvent(Order order, String before, String after, Long actorId, String reason) {
        OrderStatusEvent event = new OrderStatusEvent();
        event.setOrder(order);
        event.setActorUserId(actorId);
        event.setFromStatus(before);
        event.setToStatus(after);
        event.setPaymentStatus(order.getPaymentStatus());
        event.setReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        statusEventRepository.save(event);
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setBranchId(order.getBranch().getId());
        response.setBranchName(order.getBranchNameSnapshot() != null ? order.getBranchNameSnapshot() : order.getBranch().getName());
        response.setBranchAddress(order.getBranchAddressSnapshot() != null ? order.getBranchAddressSnapshot() : order.getBranch().getAddress());
        response.setBranchPhone(order.getBranchPhoneSnapshot() != null ? order.getBranchPhoneSnapshot() : order.getBranch().getPhone());
        response.setStatus(order.getStatus());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setCustomerName(order.getCustomerName());
        response.setRecipientName(order.getRecipientName());
        response.setRecipientPhone(order.getRecipientPhone());
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());
        response.setStatusHistory(statusEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(order.getId())
                .stream().map(event -> {
                    OrderResponse.StatusEventResponse history = new OrderResponse.StatusEventResponse();
                    history.setStatus(event.getToStatus());
                    history.setPaymentStatus(event.getPaymentStatus());
                    history.setReason(event.getReason());
                    history.setCreatedAt(event.getCreatedAt());
                    return history;
                }).toList());
        response.setCancellationRequests(cancellationRequestRepository.findByOrder_IdOrderByRequestedAtAscIdAsc(order.getId())
                .stream().map(request -> {
                    OrderResponse.CancellationRequestResponse item = new OrderResponse.CancellationRequestResponse();
                    item.setId(request.getId());
                    item.setStatus(request.getStatus());
                    item.setReason(request.getReason());
                    item.setRequestedBy(request.getRequestedBy());
                    item.setRequestedAt(request.getRequestedAt());
                    item.setDecisionReason(request.getDecisionReason());
                    item.setResolvedBy(request.getResolvedBy());
                    item.setResolvedAt(request.getResolvedAt());
                    return item;
                }).toList());
        if (order.getOrderItems() != null) {
            response.setItems(order.getOrderItems().stream().map(item -> {
                OrderResponse.OrderItemResponse itemResponse = new OrderResponse.OrderItemResponse();
                itemResponse.setId(item.getId());
                if (item.getProduct() != null) itemResponse.setProductId(item.getProduct().getId());
                if (item.getPuppyListing() != null) itemResponse.setPuppyListingId(item.getPuppyListing().getId());
                itemResponse.setProductName(item.getItemNameSnapshot() != null ? item.getItemNameSnapshot()
                        : item.getProduct() != null ? item.getProduct().getName()
                        : item.getPuppyListing() != null ? item.getPuppyListing().getListingTitle() : "Sản phẩm đã ngừng bán");
                itemResponse.setQuantity(item.getQuantity());
                itemResponse.setPrice(item.getPrice());
                return itemResponse;
            }).toList());
        }
        return response;
    }
}
