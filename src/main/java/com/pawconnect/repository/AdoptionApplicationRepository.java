package com.pawconnect.repository;

import com.pawconnect.entity.AdoptionApplication;
import com.pawconnect.entity.AdoptionApplicationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdoptionApplicationRepository extends JpaRepository<AdoptionApplication, Long> {
    Optional<AdoptionApplication> findBySeedKey(String seedKey);

    List<AdoptionApplication> findByAdoptionPostId(Long adoptionPostId);

    long countByAdoptionPostIdAndStatus(Long adoptionPostId, AdoptionApplicationStatus status);
}
