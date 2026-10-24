package com.pawconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pawconnect.security.DatabaseUserDetailsService;
import com.pawconnect.security.JwtService;
import com.pawconnect.service.chat.ConversationService;
import com.pawconnect.websocket.StompJwtChannelInterceptor;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

class StompJwtChannelInterceptorTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final StompJwtChannelInterceptor interceptor = new StompJwtChannelInterceptor(
            mock(JwtService.class), mock(DatabaseUserDetailsService.class), conversationService);

    @Test
    void rejectsSendWithoutAnAuthenticatedStompPrincipal() {
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SEND, "/app/chat.send", null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsSubscriptionForNonParticipant() {
        when(conversationService.isParticipant(42L, "customer@example.test")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SUBSCRIBE, "/topic/conversation/42",
                () -> "customer@example.test"), null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void permitsSubscriptionForConversationParticipant() {
        when(conversationService.isParticipant(42L, "customer@example.test")).thenReturn(true);
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/conversation/42", () -> "customer@example.test");

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    private Message<byte[]> message(StompCommand command, String destination, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (principal != null) {
            accessor.setUser(principal);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
