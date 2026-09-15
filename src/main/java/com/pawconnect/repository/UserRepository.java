package com.pawconnect.repository;

import com.pawconnect.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findBySeedKey(String seedKey);
    boolean existsByEmailIgnoreCase(String email);
}
