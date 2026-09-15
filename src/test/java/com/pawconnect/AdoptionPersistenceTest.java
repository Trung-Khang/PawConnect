package com.pawconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class AdoptionPersistenceTest {

    @Autowired private BranchRepository branchRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DogProfileRepository dogProfileRepository;
    @Autowired private AdoptionPostRepository adoptionPostRepository;
    @Autowired private AdoptionApplicationRepository adoptionApplicationRepository;

    @Test
    void persistsSeedKeysEnumsAndRequiredRelationships() {
        Branch branch = branchRepository.save(Branch.builder()
                .name("Ho Chi Minh")
                .code("BR_HCM_01")
                .address("Quan 1, TP.HCM")
                .build());
        Role managerRole = roleRepository.save(new Role(RoleName.BRANCH_MANAGER));
        Role customerRole = roleRepository.save(new Role(RoleName.CUSTOMER));

        User manager = userRepository.save(user("manager@example.test", managerRole, branch, "user_manager_hcm"));
        User customer = userRepository.save(user("customer@example.test", customerRole, null, "user_customer_an"));
        DogProfile dog = dogProfileRepository.save(DogProfile.builder()
                .seedKey("dog_poodle_1")
                .name("May")
                .breed("Poodle")
                .size(DogSize.SMALL)
                .ageMonths(20)
                .weightKg(new BigDecimal("7.10"))
                .gender(DogGender.MALE)
                .vaccinationStatus(VaccinationStatus.FULLY_VACCINATED)
                .description("Thong tin nhan nuoi")
                .branch(branch)
                .build());
        AdoptionPost post = adoptionPostRepository.save(AdoptionPost.builder()
                .seedKey("adoption_post_poodle_1")
                .dogProfile(dog)
                .createdBy(manager)
                .title("Tim mai am cho May")
                .description("Can gia dinh cham soc lau dai")
                .healthNote("Da kiem tra suc khoe")
                .status(AdoptionPostStatus.AVAILABLE)
                .build());
        adoptionApplicationRepository.save(AdoptionApplication.builder()
                .seedKey("application_poodle_1")
                .adoptionPost(post)
                .applicant(customer)
                .message("Toi muon tim hieu them")
                .status(AdoptionApplicationStatus.PENDING)
                .build());

        assertThat(dogProfileRepository.findBySeedKey("dog_poodle_1")).isPresent();
        assertThat(adoptionPostRepository.findByStatus(AdoptionPostStatus.AVAILABLE)).hasSize(1);
        assertThat(adoptionApplicationRepository.countByAdoptionPostIdAndStatus(post.getId(), AdoptionApplicationStatus.PENDING))
                .isEqualTo(1);
        assertThat(dog.getCreatedAt()).isNotNull();
        assertThat(post.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateDogProfileSeedKey() {
        Branch branch = branchRepository.save(Branch.builder()
                .name("Ha Noi")
                .code("BR_HN_01")
                .address("Cau Giay, Ha Noi")
                .build());
        DogProfile first = dog("dog_unique", branch);
        dogProfileRepository.saveAndFlush(first);

        assertThatThrownBy(() -> dogProfileRepository.saveAndFlush(dog("dog_unique", branch)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String email, Role role, Branch branch, String seedKey) {
        User user = new User("Seed User", email, "runtime-only", null, role);
        user.updateSeedProfile(seedKey, role, branch);
        return user;
    }

    private DogProfile dog(String seedKey, Branch branch) {
        return DogProfile.builder()
                .seedKey(seedKey)
                .name("Bap")
                .breed("Poodle")
                .size(DogSize.SMALL)
                .ageMonths(18)
                .weightKg(new BigDecimal("6.50"))
                .gender(DogGender.FEMALE)
                .vaccinationStatus(VaccinationStatus.PARTIALLY_VACCINATED)
                .branch(branch)
                .build();
    }
}
