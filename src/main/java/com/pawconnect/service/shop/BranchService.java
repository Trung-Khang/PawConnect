package com.pawconnect.service.shop;

import com.pawconnect.dto.branch.BranchResponse;
import com.pawconnect.entity.Branch;

import java.util.List;

public interface BranchService {
    List<BranchResponse> getAllBranches();
}
