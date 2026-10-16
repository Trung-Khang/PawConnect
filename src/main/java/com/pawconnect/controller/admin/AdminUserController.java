package com.pawconnect.controller.admin;

import com.pawconnect.dto.admin.AdminUserResponse;
import com.pawconnect.dto.admin.AdminUserUpdateRequest;
import com.pawconnect.dto.auth.SeedUserMappingResponse;
import com.pawconnect.service.admin.AdminUserService;
import com.pawconnect.service.seed.SeedUserImportService;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final SeedUserImportService seedUserImportService;

    public AdminUserController(AdminUserService adminUserService, SeedUserImportService seedUserImportService) {
        this.adminUserService = adminUserService;
        this.seedUserImportService = seedUserImportService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return adminUserService.getAllUsers();
    }

    @PatchMapping("/users/{userId}")
    public AdminUserResponse updateUser(@PathVariable Long userId, @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserService.updateUser(userId, request);
    }

    @DeleteMapping("/users/{userId}")
    public void deleteUser(@PathVariable Long userId) {
        adminUserService.deleteUser(userId);
    }

    @GetMapping("/users/seed-mapping")
    public List<SeedUserMappingResponse> seedMapping() {
        return seedUserImportService.mappings();
    }
}
