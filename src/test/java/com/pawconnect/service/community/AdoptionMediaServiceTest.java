package com.pawconnect.service.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawconnect.dto.media.MediaAssetResponse;
import com.pawconnect.entity.AdoptionPost;
import com.pawconnect.entity.AdoptionPostStatus;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.DogGender;
import com.pawconnect.entity.DogProfile;
import com.pawconnect.entity.DogSize;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.entity.VaccinationStatus;
import com.pawconnect.repository.AdoptionPostRepository;
import com.pawconnect.repository.DogProfileRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.service.media.CloudinaryMediaService;
import com.pawconnect.service.media.MediaAccessService;
import com.pawconnect.service.media.MediaAssetType;
import com.pawconnect.service.media.MediaLocation;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class AdoptionMediaServiceTest {

    private static final String HCM_MANAGER = "manager.hcm@example.test";

    @Mock private DogProfileRepository dogProfileRepository;
    @Mock private AdoptionPostRepository adoptionPostRepository;
    @Mock private UserRepository userRepository;
    @Mock private CloudinaryMediaService cloudinaryMediaService;
    @Mock private MediaAccessService mediaAccessService;

    private AdoptionMediaService service;
    private Branch hcm;
    private Branch hanoi;
    private User hcmManager;
    private DogProfile hcmDog;

    @BeforeEach
    void setUp() {
        service = new AdoptionMediaService(dogProfileRepository, adoptionPostRepository, userRepository,
                cloudinaryMediaService, mediaAccessService);
        hcm = branch(1L, "BR_HCM_01");
        hanoi = branch(2L, "BR_HN_01");
        hcmManager = user(HCM_MANAGER, RoleName.BRANCH_MANAGER, hcm);
        hcmDog = dog(10L, hcm);
    }

    @Test
    void managerCanReplaceOwnDogImageAndOldAssetIsRemovedAfterMetadataIsSaved() {
        MultipartFile file = mock(MultipartFile.class);
        MediaLocation location = new MediaLocation("pawconnect/branches/1/dog_profile", "dog-profile-10-new");
        MediaAssetResponse uploaded = new MediaAssetResponse("https://res.cloudinary.com/pawconnect/dog-new.png",
                "pawconnect/branches/1/dog_profile/dog-profile-10-new");
        hcmDog.setImageUrl("https://res.cloudinary.com/pawconnect/dog-old.png");
        hcmDog.setImagePublicId("pawconnect/branches/1/dog_profile/dog-profile-10-old");

        when(userRepository.findByEmailIgnoreCase(HCM_MANAGER)).thenReturn(Optional.of(hcmManager));
        when(dogProfileRepository.findById(10L)).thenReturn(Optional.of(hcmDog));
        when(mediaAccessService.createLocation(HCM_MANAGER, MediaAssetType.DOG_PROFILE, "dog-profile-10"))
                .thenReturn(location);
        when(cloudinaryMediaService.upload(file, location)).thenReturn(uploaded);
        when(dogProfileRepository.saveAndFlush(hcmDog)).thenReturn(hcmDog);

        var response = service.replaceDogProfileImage(10L, file, HCM_MANAGER);

        assertThat(response.imageUrl()).isEqualTo(uploaded.secureUrl());
        assertThat(response.imagePublicId()).isEqualTo(uploaded.publicId());
        verify(dogProfileRepository).saveAndFlush(hcmDog);
        verify(mediaAccessService).assertCanDelete(HCM_MANAGER, MediaAssetType.DOG_PROFILE,
                "pawconnect/branches/1/dog_profile/dog-profile-10-old");
        verify(cloudinaryMediaService).delete("pawconnect/branches/1/dog_profile/dog-profile-10-old");
    }

    @Test
    void managerCannotUploadAnImageForAnotherBranchDog() {
        MultipartFile file = mock(MultipartFile.class);
        DogProfile hanoiDog = dog(20L, hanoi);
        when(userRepository.findByEmailIgnoreCase(HCM_MANAGER)).thenReturn(Optional.of(hcmManager));
        when(dogProfileRepository.findById(20L)).thenReturn(Optional.of(hanoiDog));

        assertThatThrownBy(() -> service.replaceDogProfileImage(20L, file, HCM_MANAGER))
                .isInstanceOf(AccessDeniedException.class);

        verify(mediaAccessService, never()).createLocation(any(), any(), any());
        verify(cloudinaryMediaService, never()).upload(any(), any());
    }

    @Test
    void postImageCanBeDeletedOnlyByResponsibleStaff() {
        AdoptionPost post = AdoptionPost.builder()
                .dogProfile(hcmDog)
                .createdBy(hcmManager)
                .title("Tim gia dinh cho Bap")
                .description("Can nguoi cham soc phu hop.")
                .status(AdoptionPostStatus.AVAILABLE)
                .imageUrl("https://res.cloudinary.com/pawconnect/post.png")
                .imagePublicId("pawconnect/branches/1/adoption_post/adoption-post-30")
                .build();
        when(userRepository.findByEmailIgnoreCase(HCM_MANAGER)).thenReturn(Optional.of(hcmManager));
        when(adoptionPostRepository.findById(30L)).thenReturn(Optional.of(post));

        service.deleteAdoptionPostImage(30L, HCM_MANAGER);

        assertThat(post.getImageUrl()).isNull();
        assertThat(post.getImagePublicId()).isNull();
        verify(mediaAccessService).assertCanDelete(HCM_MANAGER, MediaAssetType.ADOPTION_POST,
                "pawconnect/branches/1/adoption_post/adoption-post-30");
        verify(cloudinaryMediaService).delete("pawconnect/branches/1/adoption_post/adoption-post-30");
        verify(adoptionPostRepository).save(post);
    }

    @Test
    void failedDogMetadataWriteRemovesTheNewlyUploadedAsset() {
        MultipartFile file = mock(MultipartFile.class);
        MediaLocation location = new MediaLocation("pawconnect/branches/1/dog_profile", "dog-profile-10-new");
        MediaAssetResponse uploaded = new MediaAssetResponse("https://res.cloudinary.com/pawconnect/dog-new.png",
                "pawconnect/branches/1/dog_profile/dog-profile-10-new");
        when(userRepository.findByEmailIgnoreCase(HCM_MANAGER)).thenReturn(Optional.of(hcmManager));
        when(dogProfileRepository.findById(10L)).thenReturn(Optional.of(hcmDog));
        when(mediaAccessService.createLocation(HCM_MANAGER, MediaAssetType.DOG_PROFILE, "dog-profile-10"))
                .thenReturn(location);
        when(cloudinaryMediaService.upload(file, location)).thenReturn(uploaded);
        when(dogProfileRepository.saveAndFlush(hcmDog)).thenThrow(new DataIntegrityViolationException("database rejected image metadata"));

        assertThatThrownBy(() -> service.replaceDogProfileImage(10L, file, HCM_MANAGER))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(cloudinaryMediaService).delete(uploaded.publicId());
    }

    private Branch branch(Long id, String code) {
        return Branch.builder().id(id).name(code).code(code).address("Branch address").build();
    }

    private User user(String email, RoleName roleName, Branch branch) {
        Role role = new Role(roleName);
        User user = new User("Staff", email, "runtime-only", null, role);
        user.updateSeedProfile("user_" + roleName.name().toLowerCase(), role, branch);
        return user;
    }

    private DogProfile dog(Long id, Branch branch) {
        return DogProfile.builder()
                .id(id)
                .name("Bap")
                .breed("Poodle")
                .size(DogSize.SMALL)
                .ageMonths(18)
                .weightKg(new BigDecimal("5.50"))
                .gender(DogGender.MALE)
                .vaccinationStatus(VaccinationStatus.FULLY_VACCINATED)
                .branch(branch)
                .build();
    }
}
