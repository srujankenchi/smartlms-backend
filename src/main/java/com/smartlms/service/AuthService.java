package com.smartlms.service;

import com.smartlms.dto.*;
import com.smartlms.entity.Role;
import com.smartlms.entity.User;
import com.smartlms.exception.BadRequestException;
import com.smartlms.repository.UserRepository;
import com.smartlms.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * Handles registration, login, and current-user retrieval.
 * <p>
 * Business rules:
 * <ul>
 *   <li>Registration always creates a ROLE_STUDENT account</li>
 *   <li>Duplicate emails are rejected with 400</li>
 *   <li>Passwords are BCrypt-hashed before storage</li>
 *   <li>Login authenticates via Spring Security's AuthenticationManager</li>
 *   <li>A JWT is issued on successful login or registration</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Register a new student account.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Duplicate-email check
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered: " + request.getEmail());
        }

        // Public registration unconditionally assigns ROLE_STUDENT.
        // Any role passed in the request is strictly ignored for security.
        Role assignedRole = Role.ROLE_STUDENT;

        User user = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .enabled(true)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered with role {}: {}", savedUser.getRole(), savedUser.getEmail());

        // Build Spring Security UserDetails for token generation
        UserDetails userDetails = buildUserDetails(savedUser);
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .user(UserResponse.fromEntity(savedUser))
                .build();
    }

    /**
     * Authenticate and issue a JWT.
     */
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            normalizedEmail,
                            request.getPassword()
                    )
            );
        } catch (org.springframework.security.core.AuthenticationException e) {
            throw new BadRequestException("Invalid email or password");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!user.isEnabled() || !user.isActive()) {
            throw new BadRequestException("Account is disabled. Contact an administrator.");
        }

        UserDetails userDetails = buildUserDetails(user);
        String token = jwtService.generateToken(userDetails);

        log.info("User logged in: {} ({})", user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .user(UserResponse.fromEntity(user))
                .build();
    }

    /**
     * Return the profile of the currently authenticated user.
     */
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        return UserResponse.fromEntity(user);
    }

    // ── Helper ──────────────────────────────────────────────

    private UserDetails buildUserDetails(User user) {
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),
                true, true, true,
                Collections.singletonList(
                        new SimpleGrantedAuthority(user.getRole().name())
                )
        );
    }
}
