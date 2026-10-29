package com.pawconnect.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "discount_coupons")
@Getter @Setter @NoArgsConstructor
public class DiscountCoupon {
    public enum Condition { ORDER_MINIMUM, FIRST_ORDER, COMPLETED_SPA }
    public enum DiscountType { PERCENT, FIXED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING) @Column(name = "eligibility", nullable = false, length = 30)
    private Condition condition;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private DiscountType discountType;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumSubtotal = BigDecimal.ZERO;
    @Column(nullable = false)
    private int usesPerCustomer = 1;
    @Column(nullable = false)
    private boolean active = true;
}
