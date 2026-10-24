package com.trustvault.auth;

import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

@RestController
@RequestMapping("/api/auth")
public class SignupController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupController(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record SignupRequest(
            String fullName,
            String email,
            String password,
            String role) {
    }

    /**
     * Public signup endpoint. New users self-register as VIEWER role.
     * Admins can later promote them to OWNER or ADMIN via the admin console.
     */
    @PostMapping("/signup")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(@RequestBody SignupRequest req) {

        if (req == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required.");
        }

        if (req.fullName() == null || req.fullName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Full name is required.");
        }

        if (req.fullName().trim().length() > 120) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Full name must not exceed 120 characters.");
        }

        if (req.email() == null || req.email().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Email address is required.");
        }

        String normalizedEmail = req.email().trim().toLowerCase(Locale.ROOT);

        if (!normalizedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Please enter a valid email address.");
        }

        if (req.password() == null || req.password().length() < 12) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least 12 characters.");
        }
        
        String requestedRole = req.role() == null
                ? ""
                : req.role().trim().toUpperCase(Locale.ROOT);

        if (!"OWNER".equals(requestedRole)
                && !"VIEWER".equals(requestedRole)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please select Owner or Viewer.");
        }

        UserRole selectedRole = UserRole.valueOf(requestedRole);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with that email address already exists.");
        }

        AppUser user = new AppUser(
                normalizedEmail,
                req.fullName().trim(),
                passwordEncoder.encode(req.password()),
                selectedRole
        );

        AppUser saved = userRepository.save(user);

        return Map.of(
                "id",       saved.getId(),
                "email",    saved.getEmail(),
                "fullName", saved.getFullName(),
                "role",     saved.getRole().name(),
                "message",  "Account created successfully. You can now sign in."
        );
    }
}
