package com.pawconnect.service.community;

import com.pawconnect.dto.adoption.AdoptionPostResponse;
import com.pawconnect.dto.adoption.DogProfileResponse;
import com.pawconnect.dto.media.MediaAssetResponse;
import com.pawconnect.entity.AdoptionPost;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.DogProfile;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.mapper.AdoptionMapper;
import com.pawconnect.repository.AdoptionPostRepository;
import com.pawconnect.repository.DogProfileRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.service.media.CloudinaryMediaService;
import com.pawconnect.service.media.MediaAccessService;
import com.pawconnect.service.media.MediaAssetType;
import com.pawconnect.service.media.MediaLocation;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Persists Cloudinary metadata only after the caller is authorized for the Adoption record. */
@Service
public class AdoptionMediaService {

    private static final Logger LOGGER = Logger.getLogger(AdoptionMediaService.class.getName());

    private final DogProfileRepository dogProfileRepository;
    private final AdoptionPostRepository adoptionPostRepository;
    private final UserRepository userRepository;
    private final CloudinaryMediaService cloudinaryMediaService;
    private final MediaAccessService mediaAccessService;

    public AdoptionMediaService(DogProfileRepository dogProfileRepository,
                                AdoptionPostRepository adoptionPostRepository,
                                UserRepository userRepository,
                                CloudinaryMediaService cloudinaryMediaService,
                                MediaAccessService mediaAccessService) {
        this.dogProfileRepository = dogProfileRepository;
        this.adoptionPostRepository = adoptionPostRepository;
        this.userRepository = userRepository;
        this.cloudinaryMediaService = cloudinaryMediaService;
        this.mediaAccessService = mediaAccessService;
    }

    @Transactional
    public DogProfileResponse replaceDogProfileImage(Long dogId, MultipartFile file, String actorEmail) {
        User actor = requireStaff(actorEmail);
        DogProfile dog = findDog(dogId);
        assertManagesBranch(actor, dog.getBranch());

        MediaAssetResponse uploaded = upload(actorEmail, MediaAssetType.DOG_PROFILE, "dog-profile-" + dogId, file);
        String previousPublicId = dog.getImagePublicId();
        dog.setImageUrl(uploaded.secureUrl());
        dog.setImagePublicId(uploaded.publicId());
        saveDogOrRemoveNewAsset(dog, uploaded.publicId());
        deletePreviousImage(actorEmail, MediaAssetType.DOG_PROFILE, previousPublicId);
        return AdoptionMapper.toResponse(dog);
    }

    @Transactional
    public AdoptionPostResponse replaceAdoptionPostImage(Long postId, MultipartFile file, String actorEmail) {
        User actor = requireStaff(actorEmail);
        AdoptionPost post = findPost(postId);
        assertManagesBranch(actor, post.getDogProfile().getBranch());

        MediaAssetResponse uploaded = upload(actorEmail, MediaAssetType.ADOPTION_POST, "adoption-post-" + postId, file);
        String previousPublicId = post.getImagePublicId();
        post.setImageUrl(uploaded.secureUrl());
        post.setImagePublicId(uploaded.publicId());
        savePostOrRemoveNewAsset(post, uploaded.publicId());
        deletePreviousImage(actorEmail, MediaAssetType.ADOPTION_POST, previousPublicId);
        return AdoptionMapper.toResponse(post);
    }

    @Transactional
    public void deleteDogProfileImage(Long dogId, String actorEmail) {
        User actor = requireStaff(actorEmail);
        DogProfile dog = findDog(dogId);
        assertManagesBranch(actor, dog.getBranch());
        deleteCurrentImage(actorEmail, MediaAssetType.DOG_PROFILE, dog.getImagePublicId());
        dog.setImageUrl(null);
        dog.setImagePublicId(null);
        dogProfileRepository.save(dog);
    }

    @Transactional
    public void deleteAdoptionPostImage(Long postId, String actorEmail) {
        User actor = requireStaff(actorEmail);
        AdoptionPost post = findPost(postId);
        assertManagesBranch(actor, post.getDogProfile().getBranch());
        deleteCurrentImage(actorEmail, MediaAssetType.ADOPTION_POST, post.getImagePublicId());
        post.setImageUrl(null);
        post.setImagePublicId(null);
        adoptionPostRepository.save(post);
    }

    /** Removes the external asset during post deletion without blocking the database cleanup. */
    public void removeAdoptionPostAssetForPostDeletion(Long postId, String actorEmail) {
        User actor = requireStaff(actorEmail);
        AdoptionPost post = findPost(postId);
        assertManagesBranch(actor, post.getDogProfile().getBranch());
        deletePreviousImage(actorEmail, MediaAssetType.ADOPTION_POST, post.getImagePublicId());
    }

    private MediaAssetResponse upload(String actorEmail, MediaAssetType type, String assetKey, MultipartFile file) {
        MediaLocation location = mediaAccessService.createLocation(actorEmail, type, assetKey);
        return cloudinaryMediaService.upload(file, location);
    }

    private void saveDogOrRemoveNewAsset(DogProfile dog, String uploadedPublicId) {
        try {
            dogProfileRepository.saveAndFlush(dog);
        } catch (RuntimeException exception) {
            removeUploadedAsset(uploadedPublicId);
            throw exception;
        }
    }

    private void savePostOrRemoveNewAsset(AdoptionPost post, String uploadedPublicId) {
        try {
            adoptionPostRepository.saveAndFlush(post);
        } catch (RuntimeException exception) {
            removeUploadedAsset(uploadedPublicId);
            throw exception;
        }
    }

    private void deletePreviousImage(String actorEmail, MediaAssetType type, String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            mediaAccessService.assertCanDelete(actorEmail, type, publicId);
            cloudinaryMediaService.delete(publicId);
        } catch (RuntimeException exception) {
            // The replacement is already durable; report the orphan for later cleanup instead of undoing it.
            LOGGER.log(Level.WARNING, "Could not remove replaced Cloudinary asset", exception);
        }
    }

    private void deleteCurrentImage(String actorEmail, MediaAssetType type, String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        mediaAccessService.assertCanDelete(actorEmail, type, publicId);
        cloudinaryMediaService.delete(publicId);
    }

    private void removeUploadedAsset(String publicId) {
        try {
            cloudinaryMediaService.delete(publicId);
        } catch (RuntimeException cleanupException) {
            LOGGER.log(Level.WARNING, "Could not remove Cloudinary asset after a database write failure", cleanupException);
        }
    }

    private DogProfile findDog(Long dogId) {
        return dogProfileRepository.findById(dogId)
                .orElseThrow(() -> new ResourceNotFoundException("DogProfile was not found"));
    }

    private AdoptionPost findPost(Long postId) {
        return adoptionPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Adoption post was not found"));
    }

    private User requireStaff(String email) {
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        User actor = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated account was not found"));
        if (actor.getRole().getName() != RoleName.ADMIN && actor.getRole().getName() != RoleName.BRANCH_MANAGER) {
            throw new AccessDeniedException("Only ADMIN or BRANCH_MANAGER can manage adoption images");
        }
        return actor;
    }

    private void assertManagesBranch(User actor, Branch branch) {
        if (actor.getRole().getName() == RoleName.ADMIN) {
            return;
        }
        if (actor.getBranch() == null || !actor.getBranch().getId().equals(branch.getId())) {
            throw new AccessDeniedException("Branch manager can only manage images for their own branch");
        }
    }
}
