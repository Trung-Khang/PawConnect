package com.pawconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.service.media.MediaAccessService;
import com.pawconnect.service.media.MediaAssetType;
import com.pawconnect.service.media.MediaLocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class MediaAccessServiceIntegrationTest {

    @Autowired private MediaAccessService mediaAccessService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void limitsCustomersToOwnAvatarAndScopesManagersToTheirBranchFolder() {
        User customer = saveUser("media-customer@example.com", RoleName.CUSTOMER, null);
        Branch branch = new Branch();
        branch.setName("Media branch");
        branch.setAddress("Test address");
        branch.setCode("BR_MEDIA_01");
        branch = branchRepository.save(branch);
        User manager = saveUser("media-manager@example.com", RoleName.BRANCH_MANAGER, branch);

        MediaLocation avatar = mediaAccessService.createLocation(customer.getEmail(), MediaAssetType.USER_AVATAR, "ignored");
        MediaLocation product = mediaAccessService.createLocation(manager.getEmail(), MediaAssetType.PRODUCT, "product-1");

        assertThat(avatar.folder()).isEqualTo("pawconnect/users/" + customer.getId());
        assertThat(product.folder()).isEqualTo("pawconnect/branches/" + branch.getId() + "/product");
        assertThatThrownBy(() -> mediaAccessService.createLocation(customer.getEmail(), MediaAssetType.PRODUCT, "product-1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> mediaAccessService.assertCanDelete(manager.getEmail(), MediaAssetType.PRODUCT,
                "pawconnect/branches/other/product/image"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private User saveUser(String email, RoleName roleName, Branch branch) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User("Media Test", email, passwordEncoder.encode("password123"), null, role);
        user.updateSeedProfile(null, role, branch);
        return userRepository.save(user);
    }
}
