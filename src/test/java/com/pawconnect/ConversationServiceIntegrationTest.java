package com.pawconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawconnect.dto.chat.ConversationResponse;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.service.chat.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class ConversationServiceIntegrationTest {

    @Autowired private ConversationService conversationService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void onlyConversationParticipantsCanReadAndSendMessages() {
        User customer = userRepository.save(user("chat-customer@example.com", RoleName.CUSTOMER));
        User manager = userRepository.save(user("chat-manager@example.com", RoleName.BRANCH_MANAGER));
        User outsider = userRepository.save(user("chat-outsider@example.com", RoleName.CUSTOMER));

        ConversationResponse conversation = conversationService.openForAdoption(customer.getId(), manager.getId(), 9001L);
        conversationService.sendMessage(conversation.id(), "I would like to ask about the dog.", customer.getEmail());

        assertThat(conversationService.getMessages(conversation.id(), manager.getEmail(), PageRequest.of(0, 20)).getContent())
                .hasSize(1);
        assertThatThrownBy(() -> conversationService.getMessages(conversation.id(), outsider.getEmail(), PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> conversationService.sendMessage(conversation.id(), "Unauthorized", outsider.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private User user(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return new User("Test User", email, passwordEncoder.encode("password123"), null, role);
    }
}
