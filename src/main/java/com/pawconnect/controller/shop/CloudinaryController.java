package com.pawconnect.controller.shop;

import com.pawconnect.service.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class CloudinaryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudinaryController.class);

    private final CloudinaryService cloudinaryService;

    @Autowired
    public CloudinaryController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = cloudinaryService.uploadImage(file);
            return ResponseEntity.ok(Map.of("url", imageUrl));
        } catch (Exception e) {
            LOGGER.warn("Legacy Cloudinary upload failed", e);
            return ResponseEntity.status(503).body(Map.of("message", "Image upload is temporarily unavailable"));
        }
    }
}
