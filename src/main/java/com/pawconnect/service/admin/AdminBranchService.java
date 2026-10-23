package com.pawconnect.service.admin;

import com.pawconnect.dto.admin.AdminBranchResponse;
import com.pawconnect.dto.admin.AdminBranchCreateRequest;
import com.pawconnect.dto.admin.AdminBranchUpdateRequest;
import com.pawconnect.entity.Branch;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.BranchRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminBranchService {

    private final BranchRepository branchRepository;
    private final EntityManager entityManager;

    public AdminBranchService(BranchRepository branchRepository, EntityManager entityManager) {
        this.branchRepository = branchRepository;
        this.entityManager = entityManager;
    }

    public List<AdminBranchResponse> getAllBranches() {
        return branchRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminBranchResponse create(AdminBranchCreateRequest request) {
        if (branchRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessException("Mã chi nhánh đã tồn tại.");
        }
        validateCoordinates(request.latitude(), request.longitude());
        Branch branch = Branch.builder()
                .code(request.code())
                .name(request.name().trim())
                .address(request.address().trim())
                .phone(cleanPhone(request.phone()))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();
        try {
            return toResponse(branchRepository.saveAndFlush(branch));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Mã chi nhánh đã tồn tại hoặc dữ liệu không hợp lệ.");
        }
    }

    @Transactional
    public AdminBranchResponse update(Long id, AdminBranchUpdateRequest request) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh."));
        validateCoordinates(request.latitude(), request.longitude());
        branch.setName(request.name().trim());
        branch.setAddress(request.address().trim());
        branch.setPhone(cleanPhone(request.phone()));
        branch.setLatitude(request.latitude());
        branch.setLongitude(request.longitude());
        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    public void delete(Long id) {
        Branch branch = branchRepository.findByIdWithPessimisticLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh."));
        for (String entity : List.of("User", "Product", "PuppyListing", "ServiceBooking", "Order", "DogProfile", "Conversation")) {
            Long count = entityManager.createQuery("select count(e) from com.pawconnect.entity." + entity + " e where e.branch.id = :branchId", Long.class)
                    .setParameter("branchId", id)
                    .getSingleResult();
            if (count > 0) {
                throw new BusinessException("Chi nhánh đã có dữ liệu liên quan; không thể xóa. Hãy giữ chi nhánh để bảo toàn lịch sử.");
            }
        }
        try {
            branchRepository.delete(branch);
            branchRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Chi nhánh đã có dữ liệu liên quan; không thể xóa.");
        }
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("Vĩ độ không hợp lệ.");
        }
        if (longitude != null && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("Kinh độ không hợp lệ.");
        }
    }

    private String cleanPhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }

    private AdminBranchResponse toResponse(Branch branch) {
        return new AdminBranchResponse(branch.getId(), branch.getCode(), branch.getName(), branch.getAddress(),
                branch.getPhone(), branch.getLatitude(), branch.getLongitude());
    }
}
