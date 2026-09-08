package com.pawconnect.service.auth;

import com.pawconnect.dto.auth.AuthResponse;
import com.pawconnect.dto.auth.LoginRequest;
import com.pawconnect.dto.auth.RegisterRequest;
import com.pawconnect.dto.auth.UserResponse;
import com.pawconnect.entity.Role;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import com.pawconnect.exception.ResourceConflictException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.RoleRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.security.JwtService;
import io.jsonwebtoken.JwtException;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResourceConflictException("An account already exists for this email address");
        }
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role has not been initialized"));
        User user = userRepository.save(new User(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), nullableTrim(request.phone()), customerRole));
        return tokensFor(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException exception) {
            throw new BadCredentialsException("Email or password is incorrect");
        }
        return tokensFor(findByEmail(email));
    }

    public AuthResponse refresh(String refreshToken) {
        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new BadCredentialsException("Refresh token is invalid");
            }
            return tokensFor(findByEmail(jwtService.getEmail(refreshToken)));
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Refresh token is invalid");
        }
    }

    public UserResponse getCurrentUser(String email) {
        return toResponse(findByEmail(email));
    }

    private AuthResponse tokensFor(User user) {
        return new AuthResponse(jwtService.generateAccessToken(user), jwtService.generateRefreshToken(user), "Bearer", toResponse(user));
    }

    private User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account was not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getAvatarUrl(),
                user.getRole().getName().name(), user.getCreatedAt());
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String nullableTrim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
