package com.pawconnect.controller.shop;

import com.pawconnect.dto.order.CouponDtos.*;
import com.pawconnect.entity.DiscountCoupon;
import com.pawconnect.service.shop.CouponService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class CouponController {
    private final CouponService service;
    @GetMapping("/api/admin/coupons") @PreAuthorize("hasRole('ADMIN')")
    public List<DiscountCoupon> list() { return service.list(); }
    @PostMapping("/api/admin/coupons") @PreAuthorize("hasRole('ADMIN')")
    public DiscountCoupon create(@Valid @RequestBody Create request) { return service.create(request); }
    @PatchMapping("/api/admin/coupons/{id}/active") @PreAuthorize("hasRole('ADMIN')")
    public DiscountCoupon active(@PathVariable Long id, @Valid @RequestBody Active request) { return service.setActive(id, request.active()); }
    @PostMapping("/api/coupons/quote") @PreAuthorize("hasRole('CUSTOMER')")
    public Quote quote(@Valid @RequestBody QuoteRequest request) { return service.quote(request); }
}
