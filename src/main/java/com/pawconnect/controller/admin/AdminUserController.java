package com.pawconnect.controller.admin;

import com.pawconnect.dto.admin.AdminBranchResponse;
import com.pawconnect.dto.auth.SeedUserMappingResponse;
import com.pawconnect.dto.auth.UserResponse;
import com.pawconnect.service.admin.AdminBranchService;
import com.pawconnect.service.admin.AdminUserService;
import com.pawconnect.service.seed.SeedUserImportService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminBranchService adminBranchService;
    private final SeedUserImportService seedUserImportService;

    public AdminUserController(AdminUserService adminUserService, AdminBranchService adminBranchService,
                               SeedUserImportService seedUserImportService) {
        this.adminUserService = adminUserService;
        this.adminBranchService = adminBranchService;
        this.seedUserImportService = seedUserImportService;
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return adminUserService.getAllUsers();
    }

    @GetMapping("/branches")
    public List<AdminBranchResponse> branches() {
        return adminBranchService.getAllBranches();
    }

    @GetMapping("/users/seed-mapping")
    public List<SeedUserMappingResponse> seedMapping() {
        return seedUserImportService.mappings();
    }
}
