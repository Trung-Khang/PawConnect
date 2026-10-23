package com.pawconnect;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawconnect.dto.chat.ConversationResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.ConversationStatus;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.service.chat.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class ConversationServiceIntegrationTest {

    @Autowired private ConversationService conversationService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void chatMessagesUseAColumnNameThatIsSafeForMySql() {
        Integer safeColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'CHAT_MESSAGES' AND COLUMN_NAME = 'IS_READ'
                """, Integer.class);
        assertThat(safeColumnCount).isEqualTo(1);
    }

    @Test
    void customerHasOneInboxAndAnyActiveStaffCanReadOrSend() {
        Branch hcm = branch("CHAT_HCM", "Chi nhanh Chat HCM");
        Branch hn = branch("CHAT_HN", "Chi nhanh Chat HN");
        User customer = user("chat-customer@example.com", RoleName.CUSTOMER, null);
        customer.updateAvatar("https://example.test/customer-avatar.png", "customer-avatar");
        customer = userRepository.save(customer);
        User hcmManager = user("chat-hcm-manager@example.com", RoleName.BRANCH_MANAGER, hcm);
        User competingHcmManager = user("chat-hcm-manager-2@example.com", RoleName.BRANCH_MANAGER, hcm);
        User hnManager = user("chat-hn-manager@example.com", RoleName.BRANCH_MANAGER, hn);

        ConversationResponse first = conversationService.openForAdoption(customer.getId(), hcm.getId(), 9001L);
        ConversationResponse retried = conversationService.openForAdoption(customer.getId(), hcm.getId(), 9001L);
        assertThat(first.id()).isEqualTo(retried.id());
        assertThat(first.status()).isEqualTo(ConversationStatus.OPEN);
        assertThat(first.assignedStaffId()).isNull();

        ConversationResponse claimed = conversationService.claim(first.id(), hcmManager.getEmail());
        assertThat(claimed.status()).isEqualTo(ConversationStatus.CLAIMED);
        assertThat(claimed.assignedStaffId()).isEqualTo(hcmManager.getId());
        ConversationResponse secondAdoption = conversationService.openForAdoption(customer.getId(), hn.getId(), 9002L);
        assertThat(secondAdoption.id()).isEqualTo(first.id());

        conversationService.sendMessage(first.id(), "Toi muon hoi ve be cho nay.", customer.getEmail());
        assertThat(conversationService.getMyConversations(hnManager.getEmail()))
                .singleElement()
                .satisfies(inboxItem -> {
                    assertThat(inboxItem.customerName()).isEqualTo("Test User");
                    assertThat(inboxItem.customerAvatarUrl()).isEqualTo("https://example.test/customer-avatar.png");
                    assertThat(inboxItem.lastMessagePreview()).isEqualTo("Toi muon hoi ve be cho nay.");
                    assertThat(inboxItem.unreadCount()).isEqualTo(1);
                });
        assertThat(conversationService.getMessages(first.id(), hcmManager.getEmail(), PageRequest.of(0, 20)).getContent())
                .hasSize(1);
        assertThat(conversationService.getMyConversations(hcmManager.getEmail()))
                .singleElement().extracting(ConversationResponse::unreadCount).isEqualTo(1L);
        assertThat(conversationService.getMessages(first.id(), competingHcmManager.getEmail(), PageRequest.of(0, 20)).getContent())
                .hasSize(1);
        assertThat(conversationService.getMessages(first.id(), hnManager.getEmail(), PageRequest.of(0, 20)).getContent())
                .hasSize(1);
        assertThat(conversationService.sendMessage(first.id(), "Manager chi nhanh cung da phan hoi.", competingHcmManager.getEmail()).content())
                .contains("phan hoi");
        assertThat(conversationService.getMyConversations(hnManager.getEmail()))
                .singleElement()
                .satisfies(inboxItem -> {
                    assertThat(inboxItem.unreadCount()).isZero();
                    assertThat(inboxItem.assignedStaffId()).isEqualTo(competingHcmManager.getId());
                });
    }

    private Branch branch(String code, String name) {
        return branchRepository.save(Branch.builder().code(code).name(name)
                .address("Test address").phone("0900000000").build());
    }

    private User user(String email, RoleName roleName, Branch branch) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User("Test User", email, passwordEncoder.encode("password123"), null, role);
        // Keep test staff out of the Seed V3 mapping endpoint while assigning its branch.
        user.updateSeedProfile(null, role, branch);
        return userRepository.save(user);
    }
}
