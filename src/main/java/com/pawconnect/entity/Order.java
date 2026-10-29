package com.pawconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(name = "uk_orders_user_idempotency", columnNames = {"user_id", "idempotency_key"}),
        indexes = @Index(name = "idx_orders_customer_coupon_status", columnList = "user_id,coupon_id,status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // user do TV3 cung cấp, dùng userId để nối

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private String status;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "payment_status", length = 30)
    private String paymentStatus;

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "branch_name_snapshot", length = 255)
    private String branchNameSnapshot;

    @Column(name = "branch_address_snapshot", length = 255)
    private String branchAddressSnapshot;

    @Column(name = "branch_phone_snapshot", length = 50)
    private String branchPhoneSnapshot;

    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    private BigDecimal totalAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal subtotalAmount;
    @Column(precision = 19, scale = 2)
    private BigDecimal discountAmount;
    private Long couponId;
    @Column(length = 40)
    private String couponCode;
    @Column(length = 160)
    private String couponName;

    @Column(name = "created_at")
    @org.hibernate.annotations.CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems;
}
