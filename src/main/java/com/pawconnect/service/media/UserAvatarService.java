package com.pawconnect.service.media;

import com.pawconnect.dto.media.MediaAssetResponse;
import com.pawconnect.entity.User;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserAvatarService {

    private final UserRepository userRepository;
    private final MediaAccessService mediaAccessService;
    private final CloudinaryMediaService cloudinaryMediaService;

    public UserAvatarService(UserRepository userRepository, MediaAccessService mediaAccessService,
                             CloudinaryMediaService cloudinaryMediaService) {
        this.userRepository = userRepository;
        this.mediaAccessService = mediaAccessService;
        this.cloudinaryMediaService = cloudinaryMediaService;
    }

    @Transactional
    public MediaAssetResponse replaceAvatar(String email, MultipartFile file) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated account was not found"));
        MediaAssetResponse asset = cloudinaryMediaService.upload(file,
                mediaAccessService.createLocation(email, MediaAssetType.USER_AVATAR, "avatar"));
        String previousPublicId = user.getAvatarPublicId();
        user.updateAvatar(asset.secureUrl(), asset.publicId());
        userRepository.save(user);
        if (previousPublicId != null && !previousPublicId.isBlank()) {
            cloudinaryMediaService.delete(previousPublicId);
        }
        return asset;
    }

    @Transactional
    public void deleteAvatar(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated account was not found"));
        if (user.getAvatarPublicId() != null && !user.getAvatarPublicId().isBlank()) {
            mediaAccessService.assertCanDelete(email, MediaAssetType.USER_AVATAR, user.getAvatarPublicId());
            cloudinaryMediaService.delete(user.getAvatarPublicId());
        }
        user.updateAvatar(null, null);
        userRepository.save(user);
    }
}
