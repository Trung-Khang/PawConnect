package com.pawconnect.controller.chat;

import com.pawconnect.dto.chat.ChatMessageResponse;
import com.pawconnect.dto.chat.SendChatMessageRequest;
import com.pawconnect.service.chat.ConversationService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(ConversationService conversationService, SimpMessagingTemplate messagingTemplate) {
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void send(@Valid SendChatMessageRequest request, Principal principal) {
        ChatMessageResponse message = conversationService.sendMessage(request.conversationId(), request.content(), principal.getName());
        messagingTemplate.convertAndSend("/topic/conversation/" + request.conversationId(), message);
    }
}
