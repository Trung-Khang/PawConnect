package com.pawconnect.service.admin;

import com.pawconnect.dto.admin.AdminBranchCreateRequest;
import com.pawconnect.dto.admin.AdminBranchDetailResponse;
import com.pawconnect.dto.admin.AdminBranchResponse;
import com.pawconnect.dto.admin.AdminBranchUpdateRequest;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.DogProfile;
import com.pawconnect.entity.Order;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.PuppyListing;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.BranchRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

    public AdminBranchDetailResponse getBranchDetails(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh."));
        AdminBranchResponse branchResponse = toResponse(branch);

        // Top products in stock
        List<Product> products = entityManager.createQuery(
                "select p from Product p left join fetch p.category where p.branch.id = :branchId order by p.stock desc", Product.class)
                .setParameter("branchId", branchId)
                .setMaxResults(20)
                .getResultList();
        List<AdminBranchDetailResponse.ProductSummary> productSummaries = products.stream()
                .map(p -> new AdminBranchDetailResponse.ProductSummary(
                        p.getId(), p.getName(), p.getPrice(), p.getStock(),
                        p.getCategory() != null ? p.getCategory().getName() : "Phụ kiện"))
                .toList();

        // Puppies
        List<PuppyListing> puppies = entityManager.createQuery(
                "select pl from PuppyListing pl where pl.branch.id = :branchId order by pl.createdAt desc", PuppyListing.class)
                .setParameter("branchId", branchId)
                .setMaxResults(20)
                .getResultList();
        List<AdminBranchDetailResponse.PuppySummary> puppySummaries = puppies.stream()
                .map(pl -> new AdminBranchDetailResponse.PuppySummary(
                        pl.getId(), pl.getListingTitle(), pl.getBreedCode(),
                        pl.getPricePerPuppyVnd(), pl.getStock(), pl.getStatus().name()))
                .toList();

        // Adoptions
        List<DogProfile> dogs = entityManager.createQuery(
                "select dp from DogProfile dp where dp.branch.id = :branchId order by dp.createdAt desc", DogProfile.class)
                .setParameter("branchId", branchId)
                .setMaxResults(20)
                .getResultList();
        List<AdminBranchDetailResponse.AdoptionSummary> adoptionSummaries = dogs.stream()
                .map(dp -> new AdminBranchDetailResponse.AdoptionSummary(
                        dp.getId(), dp.getName(), dp.getBreed(),
                        dp.getSize() != null ? dp.getSize().name() : "MEDIUM",
                        dp.getAgeMonths(), "AVAILABLE"))
                .toList();

        // Recent orders
        List<Order> orders = entityManager.createQuery(
                "select o from Order o where o.branch.id = :branchId and o.status != 'CANCELLED' order by o.createdAt desc", Order.class)
                .setParameter("branchId", branchId)
                .setMaxResults(15)
                .getResultList();
        List<AdminBranchDetailResponse.OrderSummary> orderSummaries = orders.stream()
                .map(o -> new AdminBranchDetailResponse.OrderSummary(
                        o.getId(), o.getCustomerName(), o.getTotalAmount(),
                        o.getStatus(), o.getPaymentStatus(), o.getCreatedAt()))
                .toList();

        return new AdminBranchDetailResponse(branchResponse, productSummaries, puppySummaries, adoptionSummaries, orderSummaries);
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
        Long branchId = branch.getId();

        // Products stats
        Object[] productStats = (Object[]) entityManager.createQuery(
                "select count(p), coalesce(sum(p.stock), 0) from Product p where p.branch.id = :branchId")
                .setParameter("branchId", branchId)
                .getSingleResult();
        int productTypesCount = productStats[0] != null ? ((Number) productStats[0]).intValue() : 0;
        int productStock = productStats[1] != null ? ((Number) productStats[1]).intValue() : 0;

        // Puppy listings stats
        Object[] puppyStats = (Object[]) entityManager.createQuery(
                "select count(pl), coalesce(sum(pl.stock), 0) from PuppyListing pl where pl.branch.id = :branchId and pl.status = com.pawconnect.entity.ListingStatus.AVAILABLE")
                .setParameter("branchId", branchId)
                .getSingleResult();
        int puppyListingsCount = puppyStats[0] != null ? ((Number) puppyStats[0]).intValue() : 0;
        int puppyStock = puppyStats[1] != null ? ((Number) puppyStats[1]).intValue() : 0;

        int totalStock = productStock + puppyStock;

        // Adoption stats
        Long adoptionTotal = entityManager.createQuery(
                "select count(dp) from DogProfile dp where dp.branch.id = :branchId", Long.class)
                .setParameter("branchId", branchId)
                .getSingleResult();

        Long adoptionAvailable = entityManager.createQuery(
                "select count(ap) from AdoptionPost ap where ap.dogProfile.branch.id = :branchId and ap.status = com.pawconnect.entity.AdoptionPostStatus.AVAILABLE", Long.class)
                .setParameter("branchId", branchId)
                .getSingleResult();

        // Orders & sales stats
        Object[] orderStats = (Object[]) entityManager.createQuery(
                "select count(o), coalesce(sum(o.totalAmount), 0) from Order o where o.branch.id = :branchId and o.status != 'CANCELLED'")
                .setParameter("branchId", branchId)
                .getSingleResult();
        int soldOrdersCount = orderStats[0] != null ? ((Number) orderStats[0]).intValue() : 0;
        BigDecimal totalRevenue;
        if (orderStats[1] instanceof BigDecimal bd) {
            totalRevenue = bd;
        } else if (orderStats[1] instanceof Number num) {
            totalRevenue = BigDecimal.valueOf(num.doubleValue());
        } else {
            totalRevenue = BigDecimal.ZERO;
        }

        Object totalItemsSoldObj = entityManager.createQuery(
                "select coalesce(sum(oi.quantity), 0) from OrderItem oi where oi.order.branch.id = :branchId and oi.order.status != 'CANCELLED'")
                .setParameter("branchId", branchId)
                .getSingleResult();
        int totalItemsSold = totalItemsSoldObj != null ? ((Number) totalItemsSoldObj).intValue() : 0;

        return new AdminBranchResponse(
                branch.getId(), branch.getCode(), branch.getName(), branch.getAddress(),
                branch.getPhone(), branch.getLatitude(), branch.getLongitude(),
                totalStock, productStock, productTypesCount, puppyStock, puppyListingsCount,
                adoptionTotal != null ? adoptionTotal.intValue() : 0,
                adoptionAvailable != null ? adoptionAvailable.intValue() : 0,
                soldOrdersCount, totalItemsSold, totalRevenue);
    }
}
