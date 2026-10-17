package com.pawconnect.repository;

import com.pawconnect.entity.ChatMessage;
import com.pawconnect.entity.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Page<ChatMessage> findByConversationIdOrderBySentAtDesc(Long conversationId, Pageable pageable);
    Optional<ChatMessage> findFirstByConversationIdOrderBySentAtDesc(Long conversationId);

    @Modifying
    @Query("update ChatMessage message set message.read = true where message.conversation.id = :conversationId "
            + "and message.sender.id <> :readerId and message.read = false")
    int markUnreadFromOtherParticipantRead(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId);

    @Query("select count(message) from ChatMessage message where message.conversation.id = :conversationId "
            + "and message.sender.id <> :readerId and message.read = false")
    long countUnreadForReader(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId);

    @Query("select count(message) from ChatMessage message where message.conversation.id = :conversationId "
            + "and message.sender.role.name = :role and message.read = false")
    long countUnreadMessagesBySenderRole(@Param("conversationId") Long conversationId, @Param("role") RoleName role);
}
