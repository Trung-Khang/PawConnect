package com.pawconnect.controller.chat;

import com.pawconnect.dto.chat.ChatMessageResponse;
import com.pawconnect.dto.chat.ConversationResponse;
import com.pawconnect.dto.chat.CreateSupportConversationRequest;
import com.pawconnect.dto.chat.SendChatMessageRequest;
import com.pawconnect.dto.chat.TransferConversationRequest;
import com.pawconnect.service.chat.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ConversationController(ConversationService conversationService, SimpMessagingTemplate messagingTemplate) {
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/my")
    public List<ConversationResponse> mine(@AuthenticationPrincipal UserDetails user) {
        return conversationService.getMyConversations(user.getUsername());
    }

    @PostMapping("/support")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ConversationResponse openSupport(@Valid @RequestBody CreateSupportConversationRequest request,
                                            @AuthenticationPrincipal UserDetails user) {
        return conversationService.openSupport(request, user.getUsername());
    }

    @PostMapping("/adoption/{postId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ConversationResponse openAdoption(@PathVariable Long postId, @AuthenticationPrincipal UserDetails user) {
        return conversationService.openAdoptionConversation(postId, user.getUsername());
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ConversationResponse claim(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return conversationService.claim(id, user.getUsername());
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ConversationResponse release(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return conversationService.release(id, user.getUsername());
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasRole('ADMIN')")
    public ConversationResponse transfer(@PathVariable Long id, @Valid @RequestBody TransferConversationRequest request,
                                         @AuthenticationPrincipal UserDetails user) {
        return conversationService.transfer(id, request.staffId(), user.getUsername());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ConversationResponse close(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return conversationService.close(id, user.getUsername());
    }

    @GetMapping("/{id}/messages")
    public Page<ChatMessageResponse> messages(@PathVariable Long id, @AuthenticationPrincipal UserDetails user,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return conversationService.getMessages(id, user.getUsername(), PageRequest.of(page, size));
    }

    @PostMapping("/{id}/messages")
    public ChatMessageResponse send(@PathVariable Long id, @Valid @RequestBody SendChatMessageRequest request,
                                    @AuthenticationPrincipal UserDetails user) {
        if (!id.equals(request.conversationId())) throw new IllegalArgumentException("Conversation id does not match request path");
        ChatMessageResponse message = conversationService.sendMessage(id, request.content(), user.getUsername());
        messagingTemplate.convertAndSend("/topic/conversation/" + id, message);
        // The payload contains no message content; staff refresh their authorized inbox on receipt.
        messagingTemplate.convertAndSend("/topic/chat-inbox", Map.of("conversationId", id));
        return message;
    }
}
