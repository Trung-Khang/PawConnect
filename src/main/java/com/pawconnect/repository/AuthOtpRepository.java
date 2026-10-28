package com.pawconnect.repository;

import com.pawconnect.entity.AuthOtp;
import com.pawconnect.entity.OtpPurpose;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthOtpRepository extends JpaRepository<AuthOtp, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AuthOtp o where o.user.id = :userId and o.purpose = :purpose and o.consumedAt is null and o.invalidatedAt is null order by o.createdAt desc")
    List<AuthOtp> findActiveForUpdate(@Param("userId") Long userId, @Param("purpose") OtpPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AuthOtp o where o.user.id = :userId and o.purpose = :purpose and o.consumedAt is null and o.invalidatedAt is null order by o.createdAt desc")
    Optional<AuthOtp> findFirstActiveForUpdate(@Param("userId") Long userId, @Param("purpose") OtpPurpose purpose);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AuthOtp o where o.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
