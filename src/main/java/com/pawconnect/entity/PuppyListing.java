package com.pawconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "puppy_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PuppyListing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 100)
    private String seedKey;

    @Column(nullable = false, length = 200)
    private String listingTitle;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 100)
    private String breedCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BreedType breedType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LifeStage lifeStage;

    @Column(nullable = false)
    private Integer ageMonths;

    @Column(precision = 6, scale = 2)
    private BigDecimal currentWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DogSize currentSize;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DogSize expectedAdultSize;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerPuppyVnd;

    @Column(nullable = false)
    private Integer stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    @Column(length = 1000)
    private String imageUrl;

    @Column(length = 255)
    private String imagePublicId;

    @Column(length = 1000)
    private String healthStatus;

    @Column(length = 1000)
    private String careInstructions;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
