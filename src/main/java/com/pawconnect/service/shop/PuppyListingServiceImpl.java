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
    public List<PuppyListingResponse> getAllPuppyListings() {
        return puppyListingRepository.findAll().stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deletePuppyListing(Long id) {
        if (!puppyListingRepository.existsById(id)) {
            throw new ResourceNotFoundException("Puppy listing not found with id: " + id);
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
