package com.smartlms.service;

import com.smartlms.entity.User;
import com.smartlms.exception.BadRequestException;
import com.smartlms.exception.ResourceNotFoundException;
import com.smartlms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business-logic layer for User operations.
 * Kept minimal in Step 1 — will grow in later steps (registration, auth, etc.).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }
}
