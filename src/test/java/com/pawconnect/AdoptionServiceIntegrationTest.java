package com.pawconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawconnect.dto.adoption.AdoptionApplicationRequest;
import com.pawconnect.dto.adoption.AdoptionApplicationResponse;
import com.pawconnect.dto.adoption.AdoptionPostRequest;
import com.pawconnect.dto.adoption.AdoptionPostResponse;
import com.pawconnect.dto.adoption.AdoptionPostUpdateRequest;
import com.pawconnect.dto.adoption.DogProfileRequest;
import com.pawconnect.dto.adoption.DogProfileResponse;
import com.pawconnect.entity.AdoptionApplicationStatus;
import com.pawconnect.entity.AdoptionPostStatus;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.DogGender;
import com.pawconnect.entity.DogSize;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.entity.VaccinationStatus;
import com.pawconnect.exception.ResourceConflictException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.service.community.AdoptionService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.seed.users.enabled=false")
@Transactional
class AdoptionServiceIntegrationTest {

    private static final String ADMIN = "adoption-admin@example.test";
    private static final String HCM_MANAGER = "adoption-manager-hcm@example.test";
    private static final String HN_MANAGER = "adoption-manager-hn@example.test";
    private static final String CUSTOMER_ONE = "adoption-customer-one@example.test";
    private static final String CUSTOMER_TWO = "adoption-customer-two@example.test";

    @Autowired private AdoptionService adoptionService;
    @Autowired private BranchRepository branchRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;

    private Branch hcm;
    private Branch hanoi;

    @BeforeEach
    void prepareAccounts() {
        hcm = branchRepository.save(branch("ADOPTION_HCM", "Ho Chi Minh"));
        hanoi = branchRepository.save(branch("ADOPTION_HN", "Ha Noi"));
        Role adminRole = role(RoleName.ADMIN);
        Role managerRole = role(RoleName.BRANCH_MANAGER);
        Role customerRole = role(RoleName.CUSTOMER);
        userRepository.save(user("Adoption Admin", ADMIN, adminRole, null));
        userRepository.save(user("HCM Manager", HCM_MANAGER, managerRole, hcm));
        userRepository.save(user("HN Manager", HN_MANAGER, managerRole, hanoi));
        userRepository.save(user("Customer One", CUSTOMER_ONE, customerRole, null));
        userRepository.save(user("Customer Two", CUSTOMER_TWO, customerRole, null));
    }

    @Test
    void enforcesStaffRoleAndBranchOwnershipForDogProfilesAndPosts() {
        DogProfileResponse dog = createDog(HCM_MANAGER, hcm.getId(), "May");

        assertThatThrownBy(() -> createDog(CUSTOMER_ONE, hcm.getId(), "Bap"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adoptionService.updateDogProfile(dog.id(), dogRequest(hanoi.getId(), "May"), HN_MANAGER))
                .isInstanceOf(AccessDeniedException.class);

        DogProfileResponse adminUpdate = adoptionService.updateDogProfile(dog.id(), dogRequest(hanoi.getId(), "May"), ADMIN);
        assertThat(adminUpdate.branchId()).isEqualTo(hanoi.getId());

        assertThatThrownBy(() -> adoptionService.createPost(postRequest(dog.id()), HCM_MANAGER))
                .isInstanceOf(AccessDeniedException.class);
        AdoptionPostResponse post = adoptionService.createPost(postRequest(dog.id()), HN_MANAGER);
        assertThat(post.status()).isEqualTo(AdoptionPostStatus.AVAILABLE);
    }

    @Test
    void approveClosesPostAndRejectsEveryOtherPendingApplication() {
        DogProfileResponse dog = createDog(HCM_MANAGER, hcm.getId(), "Miu");
        AdoptionPostResponse post = adoptionService.createPost(postRequest(dog.id()), HCM_MANAGER);
        AdoptionApplicationResponse first = adoptionService.apply(post.id(), new AdoptionApplicationRequest("Toi co the cham soc lau dai."), CUSTOMER_ONE);
        AdoptionApplicationResponse second = adoptionService.apply(post.id(), new AdoptionApplicationRequest("Gia dinh toi san sang don be ve nha."), CUSTOMER_TWO);

        AdoptionApplicationResponse approved = adoptionService.approve(first.id(), HCM_MANAGER);

        assertThat(approved.status()).isEqualTo(AdoptionApplicationStatus.APPROVED);
        assertThat(adoptionService.getManagedApplications(HCM_MANAGER))
                .extracting(AdoptionApplicationResponse::status)
                .containsExactlyInAnyOrder(AdoptionApplicationStatus.APPROVED, AdoptionApplicationStatus.REJECTED);
        assertThatThrownBy(() -> adoptionService.approve(second.id(), HCM_MANAGER))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> adoptionService.getAvailablePost(post.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void preventsDuplicatePendingApplicationsAndRejectsPendingWhenPostIsClosedManually() {
        DogProfileResponse dog = createDog(HCM_MANAGER, hcm.getId(), "Nau");
        AdoptionPostResponse post = adoptionService.createPost(postRequest(dog.id()), HCM_MANAGER);
        AdoptionApplicationResponse application = adoptionService.apply(post.id(), new AdoptionApplicationRequest("Toi muon nhan be."), CUSTOMER_ONE);

        assertThatThrownBy(() -> adoptionService.apply(post.id(), new AdoptionApplicationRequest("Toi van muon nhan be."), CUSTOMER_ONE))
                .isInstanceOf(ResourceConflictException.class);

        adoptionService.updatePost(post.id(), new AdoptionPostUpdateRequest("Tim mai am cho Nau", "Can mot gia dinh phu hop.",
                "Suc khoe on dinh.", null, null, AdoptionPostStatus.CLOSED), HCM_MANAGER);

        assertThat(adoptionService.getMyApplications(CUSTOMER_ONE))
                .singleElement()
                .extracting(AdoptionApplicationResponse::status)
                .isEqualTo(AdoptionApplicationStatus.REJECTED);
        assertThatThrownBy(() -> adoptionService.approve(application.id(), HCM_MANAGER))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void managerOnlySeesApplicationsForTheirBranch() {
        DogProfileResponse hcmDog = createDog(HCM_MANAGER, hcm.getId(), "Su");
        DogProfileResponse hanoiDog = createDog(HN_MANAGER, hanoi.getId(), "Bong");
        AdoptionPostResponse hcmPost = adoptionService.createPost(postRequest(hcmDog.id()), HCM_MANAGER);
        AdoptionPostResponse hanoiPost = adoptionService.createPost(postRequest(hanoiDog.id()), HN_MANAGER);
        adoptionService.apply(hcmPost.id(), new AdoptionApplicationRequest("Toi quan tam den Su."), CUSTOMER_ONE);
        adoptionService.apply(hanoiPost.id(), new AdoptionApplicationRequest("Toi quan tam den Bong."), CUSTOMER_TWO);

        assertThat(adoptionService.getManagedApplications(HCM_MANAGER)).hasSize(1);
        assertThat(adoptionService.getManagedApplications(HN_MANAGER)).hasSize(1);
        assertThat(adoptionService.getManagedApplications(ADMIN)).hasSize(2);
        assertThatThrownBy(() -> adoptionService.getManagedApplications(CUSTOMER_ONE))
                .isInstanceOf(AccessDeniedException.class);
    }

    private DogProfileResponse createDog(String actorEmail, Long branchId, String name) {
        return adoptionService.createDogProfile(dogRequest(branchId, name), actorEmail);
    }

    private DogProfileRequest dogRequest(Long branchId, String name) {
        return new DogProfileRequest(name, "Poodle", DogSize.SMALL, 18, new BigDecimal("5.50"), DogGender.FEMALE,
                VaccinationStatus.FULLY_VACCINATED, null, null, "Tinh tinh va de cham soc.", branchId);
    }

    private AdoptionPostRequest postRequest(Long dogId) {
        return new AdoptionPostRequest(dogId, "Tim mai am cho be", "Can gia dinh yeu thuong va cham soc.",
                "Da kiem tra suc khoe.", null, null);
    }

    private Role role(RoleName name) {
        return roleRepository.findByName(name).orElseGet(() -> roleRepository.save(new Role(name)));
    }

    private Branch branch(String code, String name) {
        Branch branch = new Branch();
        branch.setCode(code);
        branch.setName(name);
        branch.setAddress("Dia chi chi nhanh");
        return branch;
    }

    private User user(String fullName, String email, Role role, Branch branch) {
        User user = new User(fullName, email, "runtime-only", null, role);
        user.updateSeedProfile("seed_" + email.replaceAll("[^a-z]", "_"), role, branch);
        return user;
    }
}
