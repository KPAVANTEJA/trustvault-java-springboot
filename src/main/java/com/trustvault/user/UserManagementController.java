package com.trustvault.user;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementController(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toMap)
                .toList();
    }

    public record CreateUserRequest(
            String email,
            String fullName,
            String password,
            UserRole role) {
    }

    @PostMapping
    @Transactional
    public Map<String, Object> createUser(
            @RequestBody CreateUserRequest req) {

        if (req.email() == null || req.email().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Email is required.");
        }
        if (req.fullName() == null || req.fullName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Full name is required.");
        }
        if (req.password() == null || req.password().length() < 12) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least 12 characters.");
        }
        if (req.role() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Role is required.");
        }
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A user with that email already exists.");
        }

        AppUser user = new AppUser(
                req.email().trim().toLowerCase(java.util.Locale.ROOT),
                req.fullName().trim(),
                passwordEncoder.encode(req.password()),
                req.role());

        AppUser saved = userRepository.save(user);
        return toMap(saved);
    }

    public record SetEnabledRequest(boolean enabled) {
    }

    @PatchMapping("/{id}/enabled")
    @Transactional
    public Map<String, Object> setEnabled(
            @PathVariable Long id,
            @RequestBody SetEnabledRequest req) {

        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."));

        user.setEnabled(req.enabled());
        userRepository.save(user);
        return toMap(user);
    }

    public record SetRoleRequest(UserRole role) {
    }

    @PatchMapping("/{id}/role")
    @Transactional
    public Map<String, Object> setRole(
            @PathVariable Long id,
            @RequestBody SetRoleRequest req) {

        if (req.role() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Role is required.");
        }

        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."));

        user.setRole(req.role());
        userRepository.save(user);
        return toMap(user);
    }

    private Map<String, Object> toMap(AppUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("email", user.getEmail());
        result.put("fullName", user.getFullName());
        result.put("role", user.getRole().name());
        result.put("enabled", user.isEnabled());
        result.put("createdAt", user.getCreatedAt() != null
                ? user.getCreatedAt().toString() : null);
        return result;
    }
}
