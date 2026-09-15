package com.pawconnect.controller.shop;

import com.pawconnect.entity.Branch;
import com.pawconnect.dto.branch.BranchResponse;
import com.pawconnect.service.shop.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    public List<BranchResponse> getAllBranches() {
        return branchService.getAllBranches();
    }
}
