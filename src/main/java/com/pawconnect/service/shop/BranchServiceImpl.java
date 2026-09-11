package com.pawconnect.service.shop;

import com.pawconnect.dto.branch.BranchResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;

    @Override
    public List<BranchResponse> getAllBranches() {
        return branchRepository.findAll().stream().map(branch -> {
            BranchResponse response = new BranchResponse();
            response.setId(branch.getId());
            response.setName(branch.getName());
            response.setAddress(branch.getAddress());
            response.setPhone(branch.getPhone());
            response.setLatitude(branch.getLatitude());
            response.setLongitude(branch.getLongitude());
            return response;
        }).collect(Collectors.toList());
    }
}
