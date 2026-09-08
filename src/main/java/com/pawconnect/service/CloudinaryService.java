package com.pawconnect.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CloudinaryService {

    // Mock implementation for UI testing without API Key
    public String uploadImage(MultipartFile file) {
        // Return a dummy image URL for now
        return "https://res.cloudinary.com/demo/image/upload/v1312461204/sample.jpg";
    }
}
