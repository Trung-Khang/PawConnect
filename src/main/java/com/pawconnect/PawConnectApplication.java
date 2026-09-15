package com.pawconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.pawconnect.config.CloudinaryProperties;
import com.pawconnect.config.JwtProperties;
import com.pawconnect.config.SeedUserProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CloudinaryProperties.class, SeedUserProperties.class})
public class PawConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(PawConnectApplication.class, args);
    }
}
