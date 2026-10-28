package com.pawconnect.repository;

import com.pawconnect.entity.Conversation;
import com.pawconnect.entity.ConversationStatus;
import com.pawconnect.entity.ConversationType;
import com.pawconnect.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByCustomerIdAndAdoptionPostId(Long customerId, Long adoptionPostId);
    Optional<Conversation> findByCustomerId(Long customerId);
    boolean existsByCustomerId(Long customerId);
    boolean existsByAssignedStaffId(Long assignedStaffId);
    Optional<Conversation> findFirstByCustomerIdAndTypeAndBranchIdAndStatusInOrderByLastMessageAtDesc(
            Long customerId, ConversationType type, Long branchId, List<ConversationStatus> statuses);
    List<Conversation> findByCustomerEmailIgnoreCaseOrderByLastMessageAtDesc(String email);
    List<Conversation> findByBranchIdOrderByLastMessageAtDesc(Long branchId);
    List<Conversation> findAllByOrderByLastMessageAtDesc();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Conversation c
            set c.assignedStaff = :staff, c.assignedAt = :now, c.status = com.pawconnect.entity.ConversationStatus.CLAIMED
            where c.id = :conversationId and c.assignedStaff is null
              and c.status = com.pawconnect.entity.ConversationStatus.OPEN
            """)
    int claimIfOpen(@Param("conversationId") Long conversationId, @Param("staff") User staff, @Param("now") Instant now);
}
