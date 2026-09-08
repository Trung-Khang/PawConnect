package com.pawconnect.websocket;

import com.pawconnect.security.DatabaseUserDetailsService;
import com.pawconnect.security.JwtService;
import com.pawconnect.service.chat.ConversationService;
import io.jsonwebtoken.JwtException;
import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.messaging.support.ChannelInterceptor;

@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final DatabaseUserDetailsService userDetailsService;
    private final ConversationService conversationService;

    public StompJwtChannelInterceptor(JwtService jwtService, DatabaseUserDetailsService userDetailsService,
                                      ConversationService conversationService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.conversationService = conversationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            return message;
        }
        if (StompCommand.SEND.equals(accessor.getCommand()) || StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal principal = accessor.getUser();
            if (principal == null) throw new AccessDeniedException("WebSocket authentication is required");
            if (StompCommand.SEND.equals(accessor.getCommand()) && !"/app/chat.send".equals(accessor.getDestination())) {
                throw new AccessDeniedException("Unsupported WebSocket destination");
            }
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                authorizeConversationSubscription(accessor.getDestination(), principal.getName());
            }
        }
        return message;
    }

    private Principal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Bearer access token is required for STOMP CONNECT");
        }
        try {
            String token = authorization.substring(7);
            if (!jwtService.isAccessToken(token)) throw new AccessDeniedException("A JWT access token is required");
            UserDetails user = userDetailsService.loadUserByUsername(jwtService.getEmail(token));
            return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AccessDeniedException("JWT token is invalid", exception);
        }
    }

    private void authorizeConversationSubscription(String destination, String email) {
        String prefix = "/topic/conversation/";
        if (destination == null || !destination.startsWith(prefix)) {
            throw new AccessDeniedException("Unsupported subscription destination");
        }
        try {
            Long conversationId = Long.valueOf(destination.substring(prefix.length()));
            if (!conversationService.isParticipant(conversationId, email)) {
                throw new AccessDeniedException("You are not a participant in this conversation");
            }
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Conversation destination is invalid", exception);
        }
    }
}
