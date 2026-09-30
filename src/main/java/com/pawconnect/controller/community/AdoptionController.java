package com.pawconnect.controller.community;

import com.pawconnect.dto.adoption.AdoptionApplicationRequest;
import com.pawconnect.dto.adoption.AdoptionApplicationResponse;
import com.pawconnect.dto.adoption.AdoptionPostResponse;
import com.pawconnect.dto.adoption.AdoptionPostUpdateRequest;
import com.pawconnect.dto.adoption.CreateAdoptionRequest;
import com.pawconnect.service.community.AdoptionService;
import com.pawconnect.service.community.AdoptionMediaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/adoptions")
public class AdoptionController {

    private final AdoptionService adoptionService;
    private final AdoptionMediaService adoptionMediaService;

    public AdoptionController(AdoptionService adoptionService, AdoptionMediaService adoptionMediaService) {
        this.adoptionService = adoptionService;
        this.adoptionMediaService = adoptionMediaService;
    }

    @GetMapping
    public List<AdoptionPostResponse> listAvailable() {
        return adoptionService.listAvailablePosts();
    }

    @GetMapping("/{id}")
    public AdoptionPostResponse getAvailable(@PathVariable Long id) {
        return adoptionService.getAvailablePost(id);
    }

    @GetMapping("/manage/posts")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public List<AdoptionPostResponse> managedPosts(@AuthenticationPrincipal UserDetails principal) {
        return adoptionService.listManagedPosts(principal.getUsername());
    }

    @GetMapping("/manage/posts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public AdoptionPostResponse managedPost(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.getManagedPost(id, principal.getUsername());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public AdoptionPostResponse create(@Valid @RequestBody CreateAdoptionRequest request,
                                       @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.createAdoption(request, principal.getUsername());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public AdoptionPostResponse update(@PathVariable Long id, @Valid @RequestBody AdoptionPostUpdateRequest request,
                                       @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.updatePost(id, request, principal.getUsername());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        adoptionService.deletePost(id, principal.getUsername());
    }

    @PostMapping(value = "/dogs/{id}/image", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public com.pawconnect.dto.adoption.DogProfileResponse replaceDogImage(@PathVariable Long id,
                                                                            @RequestParam("file") MultipartFile file,
                                                                            @AuthenticationPrincipal UserDetails principal) {
        return adoptionMediaService.replaceDogProfileImage(id, file, principal.getUsername());
    }

    @DeleteMapping("/dogs/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public void deleteDogImage(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        adoptionMediaService.deleteDogProfileImage(id, principal.getUsername());
    }

    @PostMapping(value = "/posts/{id}/image", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public AdoptionPostResponse replacePostImage(@PathVariable Long id,
                                                  @RequestParam("file") MultipartFile file,
                                                  @AuthenticationPrincipal UserDetails principal) {
        return adoptionMediaService.replaceAdoptionPostImage(id, file, principal.getUsername());
    }

    @DeleteMapping("/posts/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public void deletePostImage(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        adoptionMediaService.deleteAdoptionPostImage(id, principal.getUsername());
    }

    @PostMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public AdoptionApplicationResponse apply(@PathVariable Long id, @Valid @RequestBody AdoptionApplicationRequest request,
                                             @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.apply(id, request, principal.getUsername());
    }

    @GetMapping("/applications/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<AdoptionApplicationResponse> myApplications(@AuthenticationPrincipal UserDetails principal) {
        return adoptionService.getMyApplications(principal.getUsername());
    }

    @GetMapping("/applications/manage")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public List<AdoptionApplicationResponse> managedApplications(@AuthenticationPrincipal UserDetails principal) {
        return adoptionService.getManagedApplications(principal.getUsername());
    }

    @PutMapping("/applications/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public AdoptionApplicationResponse approve(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.approve(id, principal.getUsername());
    }

    @PutMapping("/applications/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public AdoptionApplicationResponse reject(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return adoptionService.reject(id, principal.getUsername());
    }
}
