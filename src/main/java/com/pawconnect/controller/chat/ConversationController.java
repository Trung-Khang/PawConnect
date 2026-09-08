package com.pawconnect.controller.chat;

import com.pawconnect.dto.chat.ChatMessageResponse;
import com.pawconnect.dto.chat.ConversationResponse;
import com.pawconnect.dto.chat.SendChatMessageRequest;
import com.pawconnect.service.chat.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/my")
    public List<ConversationResponse> mine(@AuthenticationPrincipal UserDetails user) {
        return conversationService.getMyConversations(user.getUsername());
    }

    @GetMapping("/{id}/messages")
    public Page<ChatMessageResponse> messages(@PathVariable Long id, @AuthenticationPrincipal UserDetails user,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return conversationService.getMessages(id, user.getUsername(), PageRequest.of(page, size));
    }

    /** REST fallback for clients that cannot establish STOMP; it applies the same participant rule. */
    @PostMapping("/{id}/messages")
    public ChatMessageResponse send(@PathVariable Long id, @Valid @RequestBody SendChatMessageRequest request,
                                    @AuthenticationPrincipal UserDetails user) {
        if (!id.equals(request.conversationId())) throw new IllegalArgumentException("Conversation id does not match request path");
        return conversationService.sendMessage(id, request.content(), user.getUsername());
    }
}
