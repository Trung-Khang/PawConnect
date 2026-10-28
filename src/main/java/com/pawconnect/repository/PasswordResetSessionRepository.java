package com.pawconnect.repository;

import com.pawconnect.entity.PasswordResetSession;
import java.util.Optional;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetSessionRepository extends JpaRepository<PasswordResetSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PasswordResetSession s join fetch s.user where s.tokenHash = :tokenHash and s.consumedAt is null")
    Optional<PasswordResetSession> findActiveForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update PasswordResetSession s set s.consumedAt = :now where s.user.id = :userId and s.consumedAt is null")
    void consumeActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PasswordResetSession s where s.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
