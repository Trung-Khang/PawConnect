package com.pawconnect.service.chat;

import com.pawconnect.dto.chat.ChatMessageResponse;
import com.pawconnect.dto.chat.ConversationResponse;
import com.pawconnect.dto.chat.CreateSupportConversationRequest;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.ChatMessage;
import com.pawconnect.entity.Conversation;
import com.pawconnect.entity.ConversationStatus;
import com.pawconnect.entity.ConversationType;
import com.pawconnect.entity.AdoptionPost;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.PuppyListing;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.exception.ResourceConflictException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.ChatMessageRepository;
import com.pawconnect.repository.ConversationRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.repository.AdoptionPostRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.PuppyListingRepository;
import com.pawconnect.util.UserDisplayName;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConversationService {

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final BranchRepository branches;
    private final AdoptionPostRepository adoptionPosts;
    private final ProductRepository products;
    private final PuppyListingRepository puppyListings;

    public ConversationService(ConversationRepository conversations, ChatMessageRepository messages,
                               UserRepository users, BranchRepository branches, AdoptionPostRepository adoptionPosts,
                               ProductRepository products, PuppyListingRepository puppyListings) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.branches = branches;
        this.adoptionPosts = adoptionPosts;
        this.products = products;
        this.puppyListings = puppyListings;
    }

    /**
     * A customer has one inbox. Adoption approval may prepare that inbox, while product and
     * adoption context are represented by messages inside it rather than separate threads.
     */
    @Transactional
    public ConversationResponse openForAdoption(Long customerId, Long branchId, Long adoptionPostId) {
        User customer = findUser(customerId);
        if (customer.getRole().getName() != RoleName.CUSTOMER) {
            throw new IllegalArgumentException("Conversation customer must have CUSTOMER role");
        }
        Branch branch = findBranch(branchId);
        Conversation conversation = conversations.findByCustomerId(customerId)
                .orElseGet(() -> conversations.save(Conversation.adoption(customer, branch, adoptionPostId)));
        reopenWhenAssignedStaffUnavailable(conversation);
        return toResponse(conversation, customer);
    }

    /** Opens the customer's single inbox and appends a product context card when supplied. */
    @Transactional
    public ConversationResponse openSupport(CreateSupportConversationRequest request, String customerEmail) {
        User customer = currentUser(customerEmail);
        requireCustomer(customer);
        Branch branch = findBranch(request.branchId());
        Conversation conversation = conversations.findByCustomerId(customer.getId())
                .orElseGet(() -> conversations.save(Conversation.support(customer, branch, request.productId(), request.puppyListingId())));
        appendSupportContext(conversation, customer, request);
        reopenWhenAssignedStaffUnavailable(conversation);
        return toResponse(conversation, customer);
    }

    @Transactional
    public ConversationResponse openAdoptionConversation(Long adoptionPostId, String customerEmail) {
        User customer = currentUser(customerEmail);
        requireCustomer(customer);
        AdoptionPost post = adoptionPosts.findById(adoptionPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Adoption post was not found"));
        Conversation conversation = conversations.findByCustomerId(customer.getId())
                .orElseGet(() -> conversations.save(Conversation.adoption(customer, post.getDogProfile().getBranch(), post.getId())));
        appendContext(conversation, ChatMessage.context(conversation, customer, "Tôi đang quan tâm về việc nhận nuôi bé cún này.",
                "/adoptions/" + post.getId(), post.getTitle(), abbreviate(post.getDescription()), post.getImageUrl()));
        return toResponse(conversation, customer);
    }

    /** Customers see their own threads; staff sees metadata for its branch queue, never another thread's messages. */
    @Transactional
    public List<ConversationResponse> getMyConversations(String email) {
        User actor = currentUser(email);
        List<Conversation> result;
        if (actor.getRole().getName() == RoleName.CUSTOMER) {
            result = conversations.findByCustomerEmailIgnoreCaseOrderByLastMessageAtDesc(actor.getEmail());
        } else if (actor.getRole().getName() == RoleName.ADMIN) {
            result = conversations.findAllByOrderByLastMessageAtDesc();
        } else if (actor.getRole().getName() == RoleName.BRANCH_MANAGER) {
            result = conversations.findAllByOrderByLastMessageAtDesc();
        } else {
            throw new AccessDeniedException("Unsupported role");
        }
        result.forEach(this::reopenWhenAssignedStaffUnavailable);
        return result.stream().map(value -> toResponse(value, actor)).toList();
    }

    @Transactional
    public ConversationResponse claim(Long conversationId, String staffEmail) {
        User staff = currentUser(staffEmail);
        Conversation conversation = findConversation(conversationId);
        requireEligibleStaff(staff, conversation);
        reopenWhenAssignedStaffUnavailable(conversation);
        int changed = conversations.claimIfOpen(conversationId, staff, Instant.now());
        if (changed == 0) {
            throw new ResourceConflictException("Cuoc tro chuyen da duoc nhan boi nhan vien khac hoac da dong.");
        }
        return toResponse(findConversation(conversationId), staff);
    }

    @Transactional
    public ConversationResponse release(Long conversationId, String actorEmail) {
        User actor = currentUser(actorEmail);
        Conversation conversation = findConversation(conversationId);
        requireStaffOrAdmin(actor, conversation);
        conversation.release();
        return toResponse(conversation, actor);
    }

    @Transactional
    public ConversationResponse transfer(Long conversationId, Long staffId, String adminEmail) {
        User admin = currentUser(adminEmail);
        if (admin.getRole().getName() != RoleName.ADMIN) {
            throw new AccessDeniedException("Only ADMIN can transfer a conversation");
        }
        Conversation conversation = findConversation(conversationId);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new ResourceConflictException("Closed conversations cannot be transferred");
        }
        User staff = findUser(staffId);
        requireEligibleStaff(staff, conversation);
        conversation.claim(staff, Instant.now());
        return toResponse(conversation, admin);
    }

    @Transactional
    public ConversationResponse close(Long conversationId, String actorEmail) {
        User actor = currentUser(actorEmail);
        Conversation conversation = findConversation(conversationId);
        requireStaffOrAdmin(actor, conversation);
        conversation.close();
        return toResponse(conversation, actor);
    }

    @Transactional
    public Page<ChatMessageResponse> getMessages(Long conversationId, String email, Pageable pageable) {
        Conversation conversation = requireReadable(conversationId, email);
        User reader = currentUser(email);
        // Opening an inbox is not an acknowledgement. Customer messages are handled by a staff reply.
        if (isCustomer(conversation, reader)) {
            messages.markUnreadFromOtherParticipantRead(conversation.getId(), reader.getId());
        }
        return messages.findByConversationIdOrderBySentAtDesc(conversation.getId(), pageable).map(this::toResponse);
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long conversationId, String content, String senderEmail) {
        Conversation conversation = findConversation(conversationId);
        User sender = currentUser(senderEmail);
        requireCanSend(conversation, sender);
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) throw new IllegalArgumentException("Message content must not be blank");
        Instant now = Instant.now();
        if (!isCustomer(conversation, sender)) {
            // The latest staff reply acknowledges customer messages and becomes the visible handler.
            conversation.claim(sender, now);
            messages.markUnreadCustomerMessagesHandled(conversation.getId());
        }
        conversation.touch(now);
        return toResponse(messages.save(new ChatMessage(conversation, sender, normalizedContent)));
    }

    public boolean isParticipant(Long conversationId, String email) {
        try {
            requireReadable(conversationId, email);
            return true;
        } catch (ResourceNotFoundException | AccessDeniedException exception) {
            return false;
        }
    }

    private Conversation requireReadable(Long conversationId, String email) {
        Conversation conversation = findConversation(conversationId);
        User actor = currentUser(email);
        reopenWhenAssignedStaffUnavailable(conversation);
        if (isCustomer(conversation, actor) || isEligibleConversationStaff(conversation, actor)) {
            return conversation;
        }
        throw new AccessDeniedException("Conversation is currently handled by another staff member");
    }

    private void requireCanSend(Conversation conversation, User actor) {
        reopenWhenAssignedStaffUnavailable(conversation);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new ResourceConflictException("Conversation is closed");
        }
        if (isCustomer(conversation, actor) || isEligibleConversationStaff(conversation, actor)) {
            return;
        }
        throw new AccessDeniedException("Only the customer or assigned staff member can send messages");
    }

    private void requireStaffOrAdmin(User actor, Conversation conversation) {
        if (actor.getRole().getName() == RoleName.ADMIN || isAssignedStaff(conversation, actor)) return;
        throw new AccessDeniedException("Only assigned staff or ADMIN can perform this action");
    }

    private void requireEligibleStaff(User staff, Conversation conversation) {
        RoleName role = staff.getRole().getName();
        if (!staff.isActive() || staff.isLocked() || (role != RoleName.ADMIN && role != RoleName.BRANCH_MANAGER)) {
            throw new AccessDeniedException("Selected staff member cannot handle conversations");
        }
    }

    private void reopenWhenAssignedStaffUnavailable(Conversation conversation) {
        User staff = conversation.getAssignedStaff();
        if (staff != null && (!staff.isActive() || staff.isLocked() || !belongsToConversationBranch(staff, conversation))) {
            conversation.release();
        }
    }

    private boolean belongsToConversationBranch(User staff, Conversation conversation) {
        RoleName role = staff.getRole().getName();
        return staff.isActive() && !staff.isLocked()
                && (role == RoleName.ADMIN || role == RoleName.BRANCH_MANAGER);
    }

    private boolean isCustomer(Conversation conversation, User actor) { return conversation.getCustomer().getId().equals(actor.getId()); }
    private boolean isAssignedStaff(Conversation conversation, User actor) {
        return conversation.getAssignedStaff() != null && conversation.getAssignedStaff().getId().equals(actor.getId());
    }
    private boolean isEligibleConversationStaff(Conversation conversation, User actor) {
        RoleName role = actor.getRole().getName();
        return actor.isActive() && !actor.isLocked()
                && (role == RoleName.ADMIN || role == RoleName.BRANCH_MANAGER);
    }
    private void appendSupportContext(Conversation conversation, User customer, CreateSupportConversationRequest request) {
        if (request.productId() != null) {
            Product product = products.findById(request.productId()).orElseThrow(() -> new ResourceNotFoundException("Product was not found"));
            appendContext(conversation, ChatMessage.context(conversation, customer, "Tôi đang quan tâm đến sản phẩm này.", "/shop?itemType=product&itemId=" + product.getId() + "#shop-section",
                    product.getName(), abbreviate(product.getDescription()), product.getImageUrl()));
        } else if (request.puppyListingId() != null) {
            PuppyListing puppy = puppyListings.findById(request.puppyListingId()).orElseThrow(() -> new ResourceNotFoundException("Puppy listing was not found"));
            appendContext(conversation, ChatMessage.context(conversation, customer, "Tôi đang quan tâm đến sản phẩm này.", "/shop?itemType=puppy&itemId=" + puppy.getId() + "#shop-section",
                    puppy.getListingTitle(), abbreviate(puppy.getDescription()), puppy.getImageUrl()));
        }
    }
    private void appendContext(Conversation conversation, ChatMessage message) {
        conversation.touch(Instant.now());
        messages.save(message);
    }
    private String abbreviate(String value) { return value == null ? "" : value.length() <= 180 ? value : value.substring(0, 177) + "..."; }
    private void requireCustomer(User user) { if (user.getRole().getName() != RoleName.CUSTOMER) throw new AccessDeniedException("Only CUSTOMER can open support conversations"); }
    private Branch requireBranch(User user) { if (user.getBranch() == null) throw new AccessDeniedException("Branch manager must be assigned to a branch"); return user.getBranch(); }
    private Conversation findConversation(Long id) { return conversations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Conversation was not found")); }
    private User findUser(Long id) { return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account was not found")); }
    private User currentUser(String email) { return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResourceNotFoundException("Account was not found")); }
    private Branch findBranch(Long id) { return branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Branch was not found")); }

    private ConversationResponse toResponse(Conversation value, User viewer) {
        User staff = value.getAssignedStaff();
        String lastMessagePreview = messages.findFirstByConversationIdOrderBySentAtDesc(value.getId())
                .map(ChatMessage::getContent).orElse("");
        long unreadCount = viewer == null ? 0
                : isCustomer(value, viewer) ? messages.countUnreadForReader(value.getId(), viewer.getId())
                : isEligibleConversationStaff(value, viewer) ? messages.countUnreadMessagesBySenderRole(value.getId(), RoleName.CUSTOMER) : 0;
        return new ConversationResponse(value.getId(), value.getType(), value.getStatus(), value.getCustomer().getId(),
                value.getCustomer().getFullName(), value.getCustomer().getAvatarUrl(),
                staff == null ? null : staff.getId(), UserDisplayName.of(staff),
                value.getBranch() == null ? null : value.getBranch().getId(), value.getAdoptionPostId(),
                value.getProductId(), value.getPuppyListingId(), value.getAssignedAt(), value.getLastMessageAt(), value.getCreatedAt(), unreadCount,
                lastMessagePreview);
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getConversation().getId(), message.getSender().getId(),
                UserDisplayName.of(message.getSender()), message.getSender().getRole().getName().name(), message.getContent(),
                message.getSentAt(), message.isRead(), message.getMessageType(), message.getContextUrl(), message.getContextTitle(),
                message.getContextSummary(), message.getContextImageUrl());
    }
}
