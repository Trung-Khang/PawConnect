package com.pawconnect.repository;

import com.pawconnect.entity.AdoptionPost;
import com.pawconnect.entity.AdoptionPostStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdoptionPostRepository extends JpaRepository<AdoptionPost, Long> {
    Optional<AdoptionPost> findBySeedKey(String seedKey);

    List<AdoptionPost> findByStatus(AdoptionPostStatus status);

    boolean existsByDogProfileIdAndStatus(Long dogProfileId, AdoptionPostStatus status);
}
