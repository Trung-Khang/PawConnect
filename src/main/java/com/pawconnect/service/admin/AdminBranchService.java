package com.pawconnect.service.admin;

import com.pawconnect.dto.admin.AdminBranchResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.repository.BranchRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminBranchService {

    private final BranchRepository branchRepository;

    public AdminBranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    public List<AdminBranchResponse> getAllBranches() {
        return branchRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AdminBranchResponse toResponse(Branch branch) {
        return new AdminBranchResponse(branch.getId(), branch.getCode(), branch.getName(), branch.getAddress(),
                branch.getPhone(), branch.getLatitude(), branch.getLongitude());
    }
}
