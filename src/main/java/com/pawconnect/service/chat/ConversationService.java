package com.pawconnect.service.chat;

import com.pawconnect.dto.chat.ChatMessageResponse;
import com.pawconnect.dto.chat.ConversationResponse;
import com.pawconnect.entity.ChatMessage;
import com.pawconnect.entity.Conversation;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.ChatMessageRepository;
import com.pawconnect.repository.ConversationRepository;
import com.pawconnect.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository, ChatMessageRepository chatMessageRepository,
                               UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
    }

    /** Called by the Adoption workflow once a customer is permitted to contact the responsible store staff. */
    @Transactional
    public ConversationResponse openForAdoption(Long customerId, Long staffId, Long adoptionPostId) {
        User customer = findUser(customerId);
        User staff = findUser(staffId);
        if (customer.getRole().getName() != RoleName.CUSTOMER) {
            throw new IllegalArgumentException("Conversation customer must have CUSTOMER role");
        }
        if (staff.getRole().getName() != RoleName.ADMIN && staff.getRole().getName() != RoleName.BRANCH_MANAGER) {
            throw new IllegalArgumentException("Conversation staff member must have ADMIN or BRANCH_MANAGER role");
        }
        Conversation conversation = conversationRepository.findByCustomerIdAndAdminIdAndAdoptionPostId(customerId, staffId, adoptionPostId)
                .orElseGet(() -> conversationRepository.save(new Conversation(customer, staff, adoptionPostId)));
        return toResponse(conversation);
    }

    public List<ConversationResponse> getMyConversations(String email) {
        return conversationRepository.findByCustomerEmailIgnoreCaseOrAdminEmailIgnoreCaseOrderByCreatedAtDesc(email, email)
                .stream().map(this::toResponse).toList();
    }

    public Page<ChatMessageResponse> getMessages(Long conversationId, String email, Pageable pageable) {
        requireParticipant(conversationId, email);
        return chatMessageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable).map(this::toResponse);
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long conversationId, String content, String senderEmail) {
        Conversation conversation = requireParticipant(conversationId, senderEmail);
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) throw new IllegalArgumentException("Message content must not be blank");
        User sender = findUserByEmail(senderEmail);
        return toResponse(chatMessageRepository.save(new ChatMessage(conversation, sender, normalizedContent)));
    }

    public boolean isParticipant(Long conversationId, String email) {
        try {
            requireParticipant(conversationId, email);
            return true;
        } catch (ResourceNotFoundException | AccessDeniedException exception) {
            return false;
        }
    }

    private Conversation requireParticipant(Long conversationId, String email) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation was not found"));
        if (!conversation.getCustomer().getEmail().equalsIgnoreCase(email)
                && !conversation.getAdmin().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("You are not a participant in this conversation");
        }
        return conversation;
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account was not found"));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new ResourceNotFoundException("Account was not found"));
    }

    private ConversationResponse toResponse(Conversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getCustomer().getId(), conversation.getAdmin().getId(),
                conversation.getAdoptionPostId(), conversation.getCreatedAt());
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getConversation().getId(), message.getSender().getId(),
                message.getContent(), message.getSentAt(), message.isRead());
    }
}
