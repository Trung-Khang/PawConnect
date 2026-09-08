package com.pawconnect.config;

import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoleDataInitializer {

    @Bean
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
