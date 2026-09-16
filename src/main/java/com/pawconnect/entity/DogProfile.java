package com.pawconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dog_profiles", indexes = {
        @Index(name = "idx_dog_profile_branch", columnList = "branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DogProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable key for Seed V3 import and cross-file references. */
    @Column(unique = true, length = 100)
    private String seedKey;

    @Column(nullable = false, length = 120)
    private String name;

    /** Canonical breed display name from Seed V3; no Breed entity is required in this phase. */
    @Column(nullable = false, length = 120)
    private String breed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DogSize size;

    @Column(nullable = false)
    private Integer ageMonths;

    @Column(precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DogGender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VaccinationStatus vaccinationStatus;

    @Column(length = 1_000)
    private String imageUrl;

    @Column(length = 255)
    private String imagePublicId;

    @Column(length = 2_000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
