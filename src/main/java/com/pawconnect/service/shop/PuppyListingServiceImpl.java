package com.pawconnect.service.shop;

import com.pawconnect.dto.product.PuppyListingRequest;
import com.pawconnect.dto.product.PuppyListingResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.PuppyListing;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.PuppyListingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.pawconnect.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import java.util.Objects;

@Service
public class PuppyListingServiceImpl implements PuppyListingService {

    private final PuppyListingRepository puppyListingRepository;
    private final CategoryRepository categoryRepository;
    private final BranchRepository branchRepository;

    @Autowired
    public PuppyListingServiceImpl(PuppyListingRepository puppyListingRepository,
                                   CategoryRepository categoryRepository,
                                   BranchRepository branchRepository) {
        this.puppyListingRepository = puppyListingRepository;
        this.categoryRepository = categoryRepository;
        this.branchRepository = branchRepository;
    }

    @Override
    @Transactional
    public PuppyListingResponse createPuppyListing(PuppyListingRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        if (SecurityUtils.hasRole("BRANCH_MANAGER")) {
            Long currentUserBranchId = SecurityUtils.getCurrentUserBranchId();
            if (!Objects.equals(currentUserBranchId, branch.getId())) {
                throw new AccessDeniedException("Branch managers can only create puppy listings for their own branch");
            }
        }

        PuppyListing puppyListing = new PuppyListing();
        mapRequestToEntity(request, puppyListing, category, branch);
        
        // Generate seedKey if null (for new creations from UI, we might just leave it null or auto-generate)
        if (puppyListing.getSeedKey() == null) {
            puppyListing.setSeedKey("PUPPY_" + System.currentTimeMillis());
        }

        PuppyListing saved = puppyListingRepository.save(puppyListing);
        return mapEntityToResponse(saved);
    }

    @Override
    @Transactional
    public PuppyListingResponse updatePuppyListing(Long id, PuppyListingRequest request) {
        PuppyListing puppyListing = puppyListingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Puppy listing not found with id: " + id));

        if (SecurityUtils.hasRole("BRANCH_MANAGER")) {
            Long currentUserBranchId = SecurityUtils.getCurrentUserBranchId();
            if (!Objects.equals(currentUserBranchId, puppyListing.getBranch().getId())) {
                throw new AccessDeniedException("Branch managers can only update puppy listings for their own branch");
            }
            if (request.getBranchId() != null && !Objects.equals(currentUserBranchId, request.getBranchId())) {
                throw new AccessDeniedException("Branch managers cannot move puppy listings to another branch");
            }
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        mapRequestToEntity(request, puppyListing, category, branch);

        PuppyListing updated = puppyListingRepository.save(puppyListing);
        return mapEntityToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PuppyListingResponse getPuppyListing(Long id) {
        PuppyListing puppyListing = puppyListingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Puppy listing not found with id: " + id));
        return mapEntityToResponse(puppyListing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PuppyListingResponse> getAllPuppyListings(Long branchId, String breedCode, String suitableSize) {
        return puppyListingRepository.findByBranchIdAndFilters(branchId, breedCode, suitableSize).stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deletePuppyListing(Long id) {
        PuppyListing puppyListing = puppyListingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Puppy listing not found with id: " + id));
        if (SecurityUtils.hasRole("BRANCH_MANAGER")) {
            Long currentUserBranchId = SecurityUtils.getCurrentUserBranchId();
            if (!Objects.equals(currentUserBranchId, puppyListing.getBranch().getId())) {
                throw new AccessDeniedException("Branch managers can only delete puppy listings for their own branch");
            }
        }
        puppyListingRepository.deleteById(id);
    }

    private void mapRequestToEntity(PuppyListingRequest request, PuppyListing entity, Category category, Branch branch) {
        entity.setListingTitle(request.getListingTitle());
        entity.setDescription(request.getDescription());
        entity.setCategory(category);
        entity.setBranch(branch);
        entity.setBreedCode(request.getBreedCode());
        entity.setBreedType(request.getBreedType());
        entity.setLifeStage(request.getLifeStage());
        entity.setAgeMonths(request.getAgeMonths());
        entity.setCurrentWeightKg(request.getCurrentWeightKg());
        entity.setCurrentSize(request.getCurrentSize());
        entity.setExpectedAdultSize(request.getExpectedAdultSize());
        entity.setPricePerPuppyVnd(request.getPricePerPuppyVnd());
        entity.setStock(request.getStock());
        entity.setStatus(request.getStatus());
        entity.setImageUrl(request.getImageUrl());
        entity.setImagePublicId(request.getImagePublicId());
        entity.setHealthStatus(request.getHealthStatus());
        entity.setCareInstructions(request.getCareInstructions());
    }

    private PuppyListingResponse mapEntityToResponse(PuppyListing entity) {
        PuppyListingResponse response = new PuppyListingResponse();
        response.setId(entity.getId());
        response.setSeedKey(entity.getSeedKey());
        response.setListingTitle(entity.getListingTitle());
        response.setDescription(entity.getDescription());
        response.setCategoryId(entity.getCategory().getId());
        response.setBranchId(entity.getBranch().getId());
        response.setBreedCode(entity.getBreedCode());
        response.setBreedType(entity.getBreedType());
        response.setLifeStage(entity.getLifeStage());
        response.setAgeMonths(entity.getAgeMonths());
        response.setCurrentWeightKg(entity.getCurrentWeightKg());
        response.setCurrentSize(entity.getCurrentSize());
        response.setExpectedAdultSize(entity.getExpectedAdultSize());
        response.setPricePerPuppyVnd(entity.getPricePerPuppyVnd());
        response.setStock(entity.getStock());
        response.setStatus(entity.getStatus());
        response.setImageUrl(entity.getImageUrl());
        response.setImagePublicId(entity.getImagePublicId());
        response.setHealthStatus(entity.getHealthStatus());
        response.setCareInstructions(entity.getCareInstructions());
        return response;
    }
}
