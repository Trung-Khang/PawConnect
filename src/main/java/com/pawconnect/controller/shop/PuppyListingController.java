package com.pawconnect.controller.shop;

import com.pawconnect.dto.product.PuppyListingRequest;
import com.pawconnect.dto.product.PuppyListingResponse;
import com.pawconnect.service.shop.PuppyListingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/puppy-listings")
public class PuppyListingController {

    private final PuppyListingService puppyListingService;

    @Autowired
    public PuppyListingController(PuppyListingService puppyListingService) {
        this.puppyListingService = puppyListingService;
    }

    @GetMapping
    public ResponseEntity<List<PuppyListingResponse>> getAllPuppyListings() {
        return ResponseEntity.ok(puppyListingService.getAllPuppyListings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PuppyListingResponse> getPuppyListing(@PathVariable Long id) {
        return ResponseEntity.ok(puppyListingService.getPuppyListing(id));
    }

    @PostMapping
    // @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')") // Temporarily disabled for dev/test
    public ResponseEntity<PuppyListingResponse> createPuppyListing(@Valid @RequestBody PuppyListingRequest request) {
        PuppyListingResponse created = puppyListingService.createPuppyListing(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    // @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<PuppyListingResponse> updatePuppyListing(@PathVariable Long id, @Valid @RequestBody PuppyListingRequest request) {
        return ResponseEntity.ok(puppyListingService.updatePuppyListing(id, request));
    }

    @DeleteMapping("/{id}")
    // @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<Void> deletePuppyListing(@PathVariable Long id) {
        puppyListingService.deletePuppyListing(id);
        return ResponseEntity.noContent().build();
    }
}
