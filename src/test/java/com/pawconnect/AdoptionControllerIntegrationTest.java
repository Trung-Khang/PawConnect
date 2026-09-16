package com.pawconnect;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawconnect.dto.adoption.AdoptionApplicationRequest;
import com.pawconnect.dto.adoption.AdoptionPostUpdateRequest;
import com.pawconnect.dto.adoption.CreateAdoptionRequest;
import com.pawconnect.dto.adoption.DogProfileRequest;
import com.pawconnect.entity.AdoptionApplication;
import com.pawconnect.entity.AdoptionApplicationStatus;
import com.pawconnect.entity.AdoptionPost;
import com.pawconnect.entity.AdoptionPostStatus;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.DogGender;
import com.pawconnect.entity.DogProfile;
import com.pawconnect.entity.DogSize;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.entity.VaccinationStatus;
import com.pawconnect.repository.AdoptionApplicationRepository;
import com.pawconnect.repository.AdoptionPostRepository;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.DogProfileRepository;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.seed.users.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AdoptionControllerIntegrationTest {

    private static final String ADMIN = "adoption-api-admin@example.test";
    private static final String HCM_MANAGER = "adoption-api-manager-hcm@example.test";
    private static final String HN_MANAGER = "adoption-api-manager-hn@example.test";
    private static final String CUSTOMER = "adoption-api-customer@example.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BranchRepository branchRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DogProfileRepository dogProfileRepository;
    @Autowired private AdoptionPostRepository adoptionPostRepository;
    @Autowired private AdoptionApplicationRepository adoptionApplicationRepository;

    private Branch hcm;
    private Branch hanoi;
    private User hcmManager;
    private User customer;
    private AdoptionPost availablePost;

    @BeforeEach
    void prepareData() {
        hcm = branch("API_HCM", "Ho Chi Minh");
        hanoi = branch("API_HN", "Ha Noi");
        Role adminRole = role(RoleName.ADMIN);
        Role managerRole = role(RoleName.BRANCH_MANAGER);
        Role customerRole = role(RoleName.CUSTOMER);
        persistUser(ADMIN, adminRole, null);
        hcmManager = persistUser(HCM_MANAGER, managerRole, hcm);
        persistUser(HN_MANAGER, managerRole, hanoi);
        customer = persistUser(CUSTOMER, customerRole, null);
        availablePost = persistPost(hcmManager, hcm, "May");
    }

    @Test
    void publicEndpointsExposeOnlyAvailablePosts() throws Exception {
        mockMvc.perform(get("/api/adoptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(availablePost.getId()))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));

        mockMvc.perform(get("/api/adoptions/{id}", availablePost.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dogName").value("May"));
    }

    @Test
    void staffCanCreateAdoptionWhileCustomerIsForbiddenAndInvalidBodyIsRejected() throws Exception {
        CreateAdoptionRequest valid = new CreateAdoptionRequest(null, dogRequest(), "Tim mai am cho Bap",
                "Can gia dinh phu hop.", "Suc khoe on dinh.", null, null);

        mockMvc.perform(post("/api/adoptions").with(user(HCM_MANAGER).roles("BRANCH_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.dogName").value("Bap"));

        mockMvc.perform(post("/api/adoptions").with(user(CUSTOMER).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/adoptions").with(user(HCM_MANAGER).roles("BRANCH_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Thiếu hồ sơ chó\",\"description\":\"Nội dung hợp lệ\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerMustAuthenticateAndCannotSubmitDuplicatePendingApplication() throws Exception {
        AdoptionApplicationRequest request = new AdoptionApplicationRequest("Toi co dieu kien cham soc lau dai.");

        mockMvc.perform(post("/api/adoptions/{id}/apply", availablePost.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/adoptions/{id}/apply", availablePost.getId()).with(user(CUSTOMER).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/adoptions/{id}/apply", availablePost.getId()).with(user(CUSTOMER).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/adoptions/applications/my").with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicantUserId").value(customer.getId()));
    }

    @Test
    void onlyResponsibleStaffCanApproveAndClosingHidesPostFromPublic() throws Exception {
        AdoptionApplication application = adoptionApplicationRepository.saveAndFlush(AdoptionApplication.builder()
                .adoptionPost(availablePost)
                .applicant(customer)
                .message("Toi muon nhan May.")
                .status(AdoptionApplicationStatus.PENDING)
                .build());

        mockMvc.perform(put("/api/adoptions/applications/{id}/approve", application.getId())
                        .with(user(HN_MANAGER).roles("BRANCH_MANAGER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/adoptions/applications/{id}/approve", application.getId())
                        .with(user(HCM_MANAGER).roles("BRANCH_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/adoptions/{id}", availablePost.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/adoptions/applications/manage").with(user(HCM_MANAGER).roles("BRANCH_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));

        AdoptionPostUpdateRequest update = new AdoptionPostUpdateRequest("Tim mai am cho May", "Noi dung moi.",
                "Suc khoe on dinh.", null, null, AdoptionPostStatus.AVAILABLE);
        mockMvc.perform(put("/api/adoptions/{id}", availablePost.getId()).with(user(HCM_MANAGER).roles("BRANCH_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isConflict());
    }

    @Test
    void responsibleStaffCanRejectPendingApplication() throws Exception {
        AdoptionApplication application = adoptionApplicationRepository.saveAndFlush(AdoptionApplication.builder()
                .adoptionPost(availablePost)
                .applicant(customer)
                .message("Toi muon tim hieu them.")
                .status(AdoptionApplicationStatus.PENDING)
                .build());

        mockMvc.perform(put("/api/adoptions/applications/{id}/reject", application.getId())
                        .with(user(HCM_MANAGER).roles("BRANCH_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    private DogProfileRequest dogRequest() {
        return new DogProfileRequest("Bap", "Poodle", DogSize.SMALL, 18, new BigDecimal("5.50"), DogGender.MALE,
                VaccinationStatus.FULLY_VACCINATED, null, null, "Tinh tinh va de cham soc.", hcm.getId());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Branch branch(String code, String name) {
        return branchRepository.findByCode(code).orElseGet(() -> {
            Branch branch = new Branch();
            branch.setCode(code);
            branch.setName(name);
            branch.setAddress("Dia chi chi nhanh");
            return branchRepository.saveAndFlush(branch);
        });
    }

    private Role role(RoleName name) {
        return roleRepository.findByName(name).orElseGet(() -> roleRepository.saveAndFlush(new Role(name)));
    }

    private User persistUser(String email, Role role, Branch branch) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User user = new User(email, email, "runtime-only", null, role);
            user.updateSeedProfile("seed_" + email.replaceAll("[^a-z]", "_"), role, branch);
            return userRepository.saveAndFlush(user);
        });
    }

    private AdoptionPost persistPost(User manager, Branch branch, String name) {
        DogProfile dog = dogProfileRepository.saveAndFlush(DogProfile.builder()
                .name(name).breed("Poodle").size(DogSize.SMALL).ageMonths(18).weightKg(new BigDecimal("5.50"))
                .gender(DogGender.MALE).vaccinationStatus(VaccinationStatus.FULLY_VACCINATED)
                .description("Thong tin nhan nuoi.").branch(branch).build());
        return adoptionPostRepository.saveAndFlush(AdoptionPost.builder().dogProfile(dog).createdBy(manager)
                .title("Tim mai am cho " + name).description("Can gia dinh phu hop.")
                .healthNote("Suc khoe on dinh.").status(AdoptionPostStatus.AVAILABLE).build());
    }
}
