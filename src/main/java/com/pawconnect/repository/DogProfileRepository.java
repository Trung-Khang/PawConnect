package com.pawconnect.repository;

import com.pawconnect.entity.DogProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DogProfileRepository extends JpaRepository<DogProfile, Long> {
    Optional<DogProfile> findBySeedKey(String seedKey);

    List<DogProfile> findByBranchId(Long branchId);
}
