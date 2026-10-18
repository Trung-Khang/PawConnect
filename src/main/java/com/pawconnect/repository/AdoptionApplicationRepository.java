package com.pawconnect.repository;

import com.pawconnect.entity.AdoptionApplication;
import com.pawconnect.entity.AdoptionApplicationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdoptionApplicationRepository extends JpaRepository<AdoptionApplication, Long> {
    Optional<AdoptionApplication> findBySeedKey(String seedKey);

    long countBySeedKeyIsNotNull();

    List<AdoptionApplication> findByAdoptionPostId(Long adoptionPostId);

    Optional<AdoptionApplication> findFirstByAdoptionPostIdAndStatusOrderByCreatedAtAsc(
            Long adoptionPostId, AdoptionApplicationStatus status);

    boolean existsByAdoptionPostId(Long adoptionPostId);

    List<AdoptionApplication> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    List<AdoptionApplication> findByAdoptionPostDogProfileBranchId(Long branchId);

    boolean existsByAdoptionPostIdAndApplicantIdAndStatus(
            Long adoptionPostId, Long applicantId, AdoptionApplicationStatus status);

    long countByAdoptionPostIdAndStatus(Long adoptionPostId, AdoptionApplicationStatus status);
}
