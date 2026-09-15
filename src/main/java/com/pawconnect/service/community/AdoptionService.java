package com.pawconnect.service.community;

import com.pawconnect.dto.adoption.AdoptionApplicationRequest;
import com.pawconnect.dto.adoption.AdoptionApplicationResponse;
import com.pawconnect.dto.adoption.AdoptionPostRequest;
import com.pawconnect.dto.adoption.AdoptionPostResponse;
import com.pawconnect.dto.adoption.AdoptionPostUpdateRequest;
import com.pawconnect.dto.adoption.DogProfileRequest;
import com.pawconnect.dto.adoption.DogProfileResponse;
import com.pawconnect.entity.AdoptionApplication;
import com.pawconnect.entity.AdoptionApplicationStatus;
import com.pawconnect.entity.AdoptionPost;
import com.pawconnect.entity.AdoptionPostStatus;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.DogProfile;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.exception.ResourceConflictException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.mapper.AdoptionMapper;
import com.pawconnect.repository.AdoptionApplicationRepository;
import com.pawconnect.repository.AdoptionPostRepository;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.DogProfileRepository;
import com.pawconnect.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns adoption business rules. Controllers must pass the authenticated email
 * instead of accepting staff or applicant IDs from a request body.
 */
@Service
@Transactional(readOnly = true)
public class AdoptionService {

    private final DogProfileRepository dogProfileRepository;
    private final AdoptionPostRepository adoptionPostRepository;
    private final AdoptionApplicationRepository adoptionApplicationRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    public AdoptionService(DogProfileRepository dogProfileRepository,
                           AdoptionPostRepository adoptionPostRepository,
                           AdoptionApplicationRepository adoptionApplicationRepository,
                           BranchRepository branchRepository,
                           UserRepository userRepository) {
        this.dogProfileRepository = dogProfileRepository;
        this.adoptionPostRepository = adoptionPostRepository;
        this.adoptionApplicationRepository = adoptionApplicationRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
    }

    public List<AdoptionPostResponse> listAvailablePosts() {
        return adoptionPostRepository.findByStatus(AdoptionPostStatus.AVAILABLE).stream()
                .map(AdoptionMapper::toResponse)
                .toList();
    }

    public AdoptionPostResponse getAvailablePost(Long postId) {
        AdoptionPost post = findPost(postId);
        if (post.getStatus() != AdoptionPostStatus.AVAILABLE) {
            throw new ResourceNotFoundException("Adoption post is not available");
        }
        return AdoptionMapper.toResponse(post);
    }

    public List<DogProfileResponse> listDogProfiles(Long branchId, String actorEmail) {
        User actor = currentUser(actorEmail);
        requireStaff(actor);
        List<DogProfile> dogs = branchId == null
                ? (actor.getRole().getName() == RoleName.ADMIN ? dogProfileRepository.findAll()
                : dogProfileRepository.findByBranchId(requireBranch(actor).getId()))
                : dogProfileRepository.findByBranchId(branchId);
        if (branchId != null) {
            assertManagesBranch(actor, branchId);
        }
        return dogs.stream().map(AdoptionMapper::toResponse).toList();
    }

    @Transactional
    public DogProfileResponse createDogProfile(DogProfileRequest request, String actorEmail) {
        User actor = currentUser(actorEmail);
        requireStaff(actor);
        validateDogRequest(request);
        Branch branch = findBranch(request.branchId());
        assertManagesBranch(actor, branch.getId());

        DogProfile dog = DogProfile.builder()
                .name(requiredText(request.name(), "Dog name"))
                .breed(requiredText(request.breed(), "Breed"))
                .size(request.size())
                .ageMonths(request.ageMonths())
                .weightKg(request.weightKg())
                .gender(request.gender())
                .vaccinationStatus(request.vaccinationStatus())
                .imageUrl(normalizeImageUrl(request.imageUrl()))
                .imagePublicId(blankToNull(request.imagePublicId()))
                .description(blankToNull(request.description()))
                .branch(branch)
                .build();
        return AdoptionMapper.toResponse(dogProfileRepository.save(dog));
    }

    @Transactional
    public DogProfileResponse updateDogProfile(Long dogId, DogProfileRequest request, String actorEmail) {
        User actor = currentUser(actorEmail);
        requireStaff(actor);
        validateDogRequest(request);
        DogProfile dog = findDog(dogId);
        assertManagesBranch(actor, dog.getBranch().getId());
        Branch branch = findBranch(request.branchId());
        assertManagesBranch(actor, branch.getId());

        dog.setName(requiredText(request.name(), "Dog name"));
        dog.setBreed(requiredText(request.breed(), "Breed"));
        dog.setSize(request.size());
        dog.setAgeMonths(request.ageMonths());
        dog.setWeightKg(request.weightKg());
        dog.setGender(request.gender());
        dog.setVaccinationStatus(request.vaccinationStatus());
        dog.setImageUrl(normalizeImageUrl(request.imageUrl()));
        dog.setImagePublicId(blankToNull(request.imagePublicId()));
        dog.setDescription(blankToNull(request.description()));
        dog.setBranch(branch);
        return AdoptionMapper.toResponse(dog);
    }

    @Transactional
    public void deleteDogProfile(Long dogId, String actorEmail) {
        User actor = currentUser(actorEmail);
        requireStaff(actor);
        DogProfile dog = findDog(dogId);
        assertManagesBranch(actor, dog.getBranch().getId());
        if (adoptionPostRepository.existsByDogProfileId(dogId)) {
            throw new ResourceConflictException("DogProfile with adoption history cannot be deleted");
        }
        dogProfileRepository.delete(dog);
    }

    @Transactional
    public AdoptionPostResponse createPost(AdoptionPostRequest request, String actorEmail) {
        User actor = currentUser(actorEmail);
        requireStaff(actor);
        if (request == null || request.dogProfileId() == null) {
            throw new IllegalArgumentException("dogProfileId is required");
        }
        DogProfile dog = findDog(request.dogProfileId());
        assertManagesBranch(actor, dog.getBranch().getId());
        if (adoptionPostRepository.existsByDogProfileIdAndStatus(dog.getId(), AdoptionPostStatus.AVAILABLE)) {
            throw new ResourceConflictException("DogProfile already has an available adoption post");
        }

        AdoptionPost post = AdoptionPost.builder()
                .dogProfile(dog)
                .createdBy(actor)
                .title(requiredText(request.title(), "Title"))
                .description(requiredText(request.description(), "Description"))
                .healthNote(blankToNull(request.healthNote()))
                .status(AdoptionPostStatus.AVAILABLE)
                .imageUrl(normalizeImageUrl(request.imageUrl()))
                .imagePublicId(blankToNull(request.imagePublicId()))
                .build();
        return AdoptionMapper.toResponse(adoptionPostRepository.save(post));
    }

    @Transactional
    public AdoptionPostResponse updatePost(Long postId, AdoptionPostUpdateRequest request, String actorEmail) {
        User actor = currentUser(actorEmail);
        requireStaff(actor);
        AdoptionPost post = adoptionPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Adoption post was not found"));
        assertManagesBranch(actor, post.getDogProfile().getBranch().getId());
        if (request == null) {
            throw new IllegalArgumentException("Adoption post update is required");
        }

        AdoptionPostStatus requestedStatus = request.status() == null ? post.getStatus() : request.status();
        if (requestedStatus == AdoptionPostStatus.AVAILABLE
                && adoptionApplicationRepository.countByAdoptionPostIdAndStatus(postId, AdoptionApplicationStatus.APPROVED) > 0) {
            throw new ResourceConflictException("An approved adoption post cannot be reopened");
        }
        post.setTitle(requiredText(request.title(), "Title"));
        post.setDescription(requiredText(request.description(), "Description"));
        post.setHealthNote(blankToNull(request.healthNote()));
        post.setImageUrl(normalizeImageUrl(request.imageUrl()));
        post.setImagePublicId(blankToNull(request.imagePublicId()));
        post.setStatus(requestedStatus);
        if (requestedStatus == AdoptionPostStatus.CLOSED) {
            rejectPendingApplications(postId);
        }
        return AdoptionMapper.toResponse(post);
    }

    @Transactional
    public AdoptionApplicationResponse apply(Long postId, AdoptionApplicationRequest request, String actorEmail) {
        User applicant = currentUser(actorEmail);
        requireRole(applicant, RoleName.CUSTOMER);
        AdoptionPost post = adoptionPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Adoption post was not found"));
        if (post.getStatus() != AdoptionPostStatus.AVAILABLE) {
            throw new ResourceConflictException("Only available adoption posts accept applications");
        }
        if (adoptionApplicationRepository.existsByAdoptionPostIdAndApplicantIdAndStatus(
                postId, applicant.getId(), AdoptionApplicationStatus.PENDING)) {
            throw new ResourceConflictException("Customer already has a pending application for this adoption post");
        }
        String message = request == null ? null : requiredText(request.message(), "Application message");
        AdoptionApplication application = AdoptionApplication.builder()
                .adoptionPost(post)
                .applicant(applicant)
                .message(message)
                .status(AdoptionApplicationStatus.PENDING)
                .build();
        return AdoptionMapper.toResponse(adoptionApplicationRepository.save(application));
    }

    public List<AdoptionApplicationResponse> getMyApplications(String actorEmail) {
        User applicant = currentUser(actorEmail);
        requireRole(applicant, RoleName.CUSTOMER);
        return adoptionApplicationRepository.findByApplicantIdOrderByCreatedAtDesc(applicant.getId()).stream()
                .map(AdoptionMapper::toResponse)
                .toList();
    }

    public List<AdoptionApplicationResponse> getManagedApplications(String actorEmail) {
        User actor = currentUser(actorEmail);
        requireStaff(actor);
        List<AdoptionApplication> applications = actor.getRole().getName() == RoleName.ADMIN
                ? adoptionApplicationRepository.findAll()
                : adoptionApplicationRepository.findByAdoptionPostDogProfileBranchId(requireBranch(actor).getId());
        return applications.stream().map(AdoptionMapper::toResponse).toList();
    }

    @Transactional
    public AdoptionApplicationResponse approve(Long applicationId, String actorEmail) {
        User actor = currentUser(actorEmail);
        requireStaff(actor);
        AdoptionApplication application = findApplication(applicationId);
        AdoptionPost post = adoptionPostRepository.findByIdForUpdate(application.getAdoptionPost().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Adoption post was not found"));
        assertManagesBranch(actor, post.getDogProfile().getBranch().getId());
        if (post.getStatus() != AdoptionPostStatus.AVAILABLE || application.getStatus() != AdoptionApplicationStatus.PENDING) {
            throw new ResourceConflictException("Only a pending application on an available post can be approved");
        }
        application.setStatus(AdoptionApplicationStatus.APPROVED);
        post.setStatus(AdoptionPostStatus.CLOSED);
        rejectPendingApplicationsExcept(post.getId(), applicationId);
        return AdoptionMapper.toResponse(application);
    }

    @Transactional
    public AdoptionApplicationResponse reject(Long applicationId, String actorEmail) {
        User actor = currentUser(actorEmail);
        requireStaff(actor);
        AdoptionApplication application = findApplication(applicationId);
        AdoptionPost post = application.getAdoptionPost();
        assertManagesBranch(actor, post.getDogProfile().getBranch().getId());
        if (application.getStatus() != AdoptionApplicationStatus.PENDING) {
            throw new ResourceConflictException("Only a pending application can be rejected");
        }
        application.setStatus(AdoptionApplicationStatus.REJECTED);
        return AdoptionMapper.toResponse(application);
    }

    private void rejectPendingApplications(Long postId) {
        adoptionApplicationRepository.findByAdoptionPostId(postId).stream()
                .filter(application -> application.getStatus() == AdoptionApplicationStatus.PENDING)
                .forEach(application -> application.setStatus(AdoptionApplicationStatus.REJECTED));
    }

    private void rejectPendingApplicationsExcept(Long postId, Long approvedApplicationId) {
        adoptionApplicationRepository.findByAdoptionPostId(postId).stream()
                .filter(application -> !application.getId().equals(approvedApplicationId))
                .filter(application -> application.getStatus() == AdoptionApplicationStatus.PENDING)
                .forEach(application -> application.setStatus(AdoptionApplicationStatus.REJECTED));
    }

    private DogProfile findDog(Long dogId) {
        return dogProfileRepository.findById(dogId)
                .orElseThrow(() -> new ResourceNotFoundException("DogProfile was not found"));
    }

    private AdoptionPost findPost(Long postId) {
        return adoptionPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Adoption post was not found"));
    }

    private AdoptionApplication findApplication(Long applicationId) {
        return adoptionApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Adoption application was not found"));
    }

    private Branch findBranch(Long branchId) {
        if (branchId == null) {
            throw new IllegalArgumentException("branchId is required");
        }
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch was not found"));
    }

    private User currentUser(String email) {
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated account was not found"));
    }

    private void requireStaff(User actor) {
        if (actor.getRole().getName() != RoleName.ADMIN && actor.getRole().getName() != RoleName.BRANCH_MANAGER) {
            throw new AccessDeniedException("Only ADMIN or BRANCH_MANAGER can manage adoption records");
        }
    }

    private void requireRole(User actor, RoleName role) {
        if (actor.getRole().getName() != role) {
            throw new AccessDeniedException("This action requires " + role + " role");
        }
    }

    private void assertManagesBranch(User actor, Long branchId) {
        if (actor.getRole().getName() == RoleName.ADMIN) {
            return;
        }
        Branch branch = requireBranch(actor);
        if (!branch.getId().equals(branchId)) {
            throw new AccessDeniedException("Branch manager can only manage their own branch");
        }
    }

    private Branch requireBranch(User actor) {
        if (actor.getBranch() == null) {
            throw new AccessDeniedException("Branch manager must be assigned to a branch");
        }
        return actor.getBranch();
    }

    private void validateDogRequest(DogProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("DogProfile request is required");
        }
        requiredText(request.name(), "Dog name");
        requiredText(request.breed(), "Breed");
        if (request.size() == null || request.gender() == null || request.vaccinationStatus() == null) {
            throw new IllegalArgumentException("Dog size, gender, and vaccinationStatus are required");
        }
        if (request.ageMonths() == null || request.ageMonths() < 0) {
            throw new IllegalArgumentException("ageMonths must be zero or greater");
        }
        BigDecimal weight = request.weightKg();
        if (weight != null && weight.signum() <= 0) {
            throw new IllegalArgumentException("weightKg must be positive when provided");
        }
        normalizeImageUrl(request.imageUrl());
    }

    private String normalizeImageUrl(String imageUrl) {
        String normalized = blankToNull(imageUrl);
        if (normalized != null && !normalized.startsWith("https://")) {
            throw new IllegalArgumentException("imageUrl must use HTTPS");
        }
        return normalized;
    }

    private String requiredText(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
