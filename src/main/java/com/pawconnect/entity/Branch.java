package com.pawconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    private String phone;

    private Double latitude;

    private Double longitude;

    @JsonIgnore
    @OneToMany(mappedBy = "branch")
    private List<Product> products;

    @JsonIgnore
    @OneToMany(mappedBy = "branch")
    private List<ServiceBooking> serviceBookings;
}
