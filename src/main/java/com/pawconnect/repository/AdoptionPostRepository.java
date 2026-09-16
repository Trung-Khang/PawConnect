package com.pawconnect.repository;

import com.pawconnect.entity.AdoptionPost;
import com.pawconnect.entity.AdoptionPostStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdoptionPostRepository extends JpaRepository<AdoptionPost, Long> {
    Optional<AdoptionPost> findBySeedKey(String seedKey);

    List<AdoptionPost> findByStatus(AdoptionPostStatus status);

    List<AdoptionPost> findByDogProfileBranchId(Long branchId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select post from AdoptionPost post where post.id = :id")
    Optional<AdoptionPost> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByDogProfileId(Long dogProfileId);

    boolean existsByDogProfileIdAndStatus(Long dogProfileId, AdoptionPostStatus status);
}
