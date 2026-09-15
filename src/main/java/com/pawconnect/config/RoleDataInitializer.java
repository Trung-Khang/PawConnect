package com.pawconnect.config;

import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class RoleDataInitializer {

    @Bean
    @Order(10)
    CommandLineRunner initializeRoles(RoleRepository roleRepository) {
        return arguments -> {
            for (RoleName roleName : RoleName.values()) {
                if (roleRepository.findByName(roleName).isEmpty()) {
                    roleRepository.save(new Role(roleName));
                }
            }
        };
    }
}
