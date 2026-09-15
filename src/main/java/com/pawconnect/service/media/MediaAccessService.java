package com.pawconnect.service.media;

import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MediaAccessService {

    private final UserRepository userRepository;

    public MediaAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public MediaLocation createLocation(String email, MediaAssetType type, String assetKey) {
        User user = currentUser(email);
        String key = normalizeKey(assetKey);
        RoleName role = user.getRole().getName();

        if (type == MediaAssetType.USER_AVATAR) {
            return new MediaLocation("pawconnect/users/" + user.getId(), "avatar-" + UUID.randomUUID());
        }
        if (role == RoleName.ADMIN) {
            return new MediaLocation("pawconnect/" + type.name().toLowerCase(Locale.ROOT), key + "-" + UUID.randomUUID());
        }
        if (role == RoleName.BRANCH_MANAGER && user.getBranch() != null) {
            return new MediaLocation("pawconnect/branches/" + user.getBranch().getId() + "/" + type.name().toLowerCase(Locale.ROOT),
                    key + "-" + UUID.randomUUID());
        }
        throw new AccessDeniedException("This account cannot manage " + type + " images");
    }

    public void assertCanDelete(String email, MediaAssetType type, String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("publicId is required");
        }
        User user = currentUser(email);
        if (user.getRole().getName() == RoleName.ADMIN && publicId.startsWith("pawconnect/")) {
            return;
        }
        MediaLocation location = createLocation(email, type, "delete-check");
        if (!publicId.startsWith(location.publicIdPrefix())) {
            throw new AccessDeniedException("This account cannot delete the requested image");
        }
    }

    private User currentUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated account was not found"));
    }

    private String normalizeKey(String assetKey) {
        if (assetKey == null || !assetKey.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")) {
            throw new IllegalArgumentException("assetKey must contain only letters, digits, hyphens, or underscores");
        }
        return assetKey;
    }
}
