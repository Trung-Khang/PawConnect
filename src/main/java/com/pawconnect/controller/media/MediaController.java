package com.pawconnect.controller.media;

import com.pawconnect.dto.media.MediaAssetResponse;
import com.pawconnect.dto.media.MediaReplacementResponse;
import com.pawconnect.service.media.CloudinaryMediaService;
import com.pawconnect.service.media.MediaAccessService;
import com.pawconnect.service.media.MediaAssetType;
import com.pawconnect.service.media.UserAvatarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaAccessService mediaAccessService;
    private final CloudinaryMediaService cloudinaryMediaService;
    private final UserAvatarService userAvatarService;

    public MediaController(MediaAccessService mediaAccessService, CloudinaryMediaService cloudinaryMediaService,
                           UserAvatarService userAvatarService) {
        this.mediaAccessService = mediaAccessService;
        this.cloudinaryMediaService = cloudinaryMediaService;
        this.userAvatarService = userAvatarService;
    }

    /** Uploads a business image; its owning TV1/TV2 module persists the returned metadata. */
    @PostMapping(value = "/{type}", consumes = "multipart/form-data")
    public ResponseEntity<MediaAssetResponse> upload(@PathVariable MediaAssetType type,
                                                      @RequestParam MultipartFile file,
                                                      @RequestParam String assetKey,
                                                      @AuthenticationPrincipal UserDetails principal) {
        rejectAvatar(type);
        MediaAssetResponse response = cloudinaryMediaService.upload(file,
                mediaAccessService.createLocation(principal.getUsername(), type, assetKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** The caller saves the new metadata first, then calls DELETE for previousPublicId. */
    @PostMapping(value = "/{type}/replace", consumes = "multipart/form-data")
    public MediaReplacementResponse replace(@PathVariable MediaAssetType type,
                                            @RequestParam MultipartFile file,
                                            @RequestParam String assetKey,
                                            @RequestParam String previousPublicId,
                                            @AuthenticationPrincipal UserDetails principal) {
        rejectAvatar(type);
        mediaAccessService.assertCanDelete(principal.getUsername(), type, previousPublicId);
        MediaAssetResponse asset = cloudinaryMediaService.upload(file,
                mediaAccessService.createLocation(principal.getUsername(), type, assetKey));
        return new MediaReplacementResponse(asset, previousPublicId);
    }

    @DeleteMapping("/{type}")
    public ResponseEntity<Void> delete(@PathVariable MediaAssetType type, @RequestParam String publicId,
                                       @AuthenticationPrincipal UserDetails principal) {
        rejectAvatar(type);
        mediaAccessService.assertCanDelete(principal.getUsername(), type, publicId);
        cloudinaryMediaService.delete(publicId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/avatar", consumes = "multipart/form-data")
    public MediaAssetResponse replaceAvatar(@RequestParam MultipartFile file, @AuthenticationPrincipal UserDetails principal) {
        return userAvatarService.replaceAvatar(principal.getUsername(), file);
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal UserDetails principal) {
        userAvatarService.deleteAvatar(principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    private void rejectAvatar(MediaAssetType type) {
        if (type == MediaAssetType.USER_AVATAR) {
            throw new AccessDeniedException("Use /api/media/avatar to manage the authenticated user's avatar");
        }
    }
}
