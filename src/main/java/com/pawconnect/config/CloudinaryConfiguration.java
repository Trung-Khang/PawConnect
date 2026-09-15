package com.pawconnect.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.cloudinary", name = "enabled", havingValue = "true")
    Cloudinary cloudinary(CloudinaryProperties properties) {
        require("CLOUDINARY_CLOUD_NAME", properties.cloudName());
        require("CLOUDINARY_API_KEY", properties.apiKey());
        require("CLOUDINARY_API_SECRET", properties.apiSecret());
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.cloudName(),
                "api_key", properties.apiKey(),
                "api_secret", properties.apiSecret(),
                "secure", true));
    }

    private void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when Cloudinary is enabled");
        }
    }
}
