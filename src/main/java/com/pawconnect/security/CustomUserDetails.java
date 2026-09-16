package com.pawconnect.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class CustomUserDetails extends User {
    
    private final Long id;
    private final Long branchId;

    public CustomUserDetails(Long id, Long branchId, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.id = id;
        this.branchId = branchId;
    }

    public Long getId() {
        return id;
    }

    public Long getBranchId() {
        return branchId;
    }
}
