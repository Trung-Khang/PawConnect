package com.pawconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.pawconnect.config.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class PawConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(PawConnectApplication.class, args);
    }
}
