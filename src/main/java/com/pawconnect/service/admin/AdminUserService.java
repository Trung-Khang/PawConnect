package com.pawconnect.service.admin;

import com.pawconnect.dto.auth.UserResponse;
import com.pawconnect.entity.User;
import com.pawconnect.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Returns account information safe for administration; password hashes are never mapped to the response. */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(this::toResponse).toList();
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getAvatarUrl(),
                user.getRole().getName().name(), user.getCreatedAt());
    }
}
