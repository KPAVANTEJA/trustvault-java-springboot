package com.trustvault.user;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoUserInitializer implements ApplicationRunner {

    private static final Logger log =
        LoggerFactory.getLogger(DemoUserInitializer.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public DemoUserInitializer(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {

        String adminEmail = environment.getProperty("TRUSTVAULT_ADMIN_EMAIL");
        String adminPassword = environment.getProperty("TRUSTVAULT_ADMIN_PASSWORD");

        String ownerEmail = environment.getProperty("TRUSTVAULT_OWNER_EMAIL");
        String ownerPassword = environment.getProperty("TRUSTVAULT_OWNER_PASSWORD");

        String viewerEmail = environment.getProperty("TRUSTVAULT_VIEWER_EMAIL");
        String viewerPassword = environment.getProperty("TRUSTVAULT_VIEWER_PASSWORD");

        // Use standard demo credentials if not explicitly configured in environment
        if (adminEmail == null || adminEmail.isBlank()) adminEmail = "admin@trustvault.local";
        if (adminPassword == null || adminPassword.isBlank()) adminPassword = "admin123456!";

        if (ownerEmail == null || ownerEmail.isBlank()) ownerEmail = "owner@trustvault.local";
        if (ownerPassword == null || ownerPassword.isBlank()) ownerPassword = "owner123456!";

        if (viewerEmail == null || viewerEmail.isBlank()) viewerEmail = "viewer@trustvault.local";
        if (viewerPassword == null || viewerPassword.isBlank()) viewerPassword = "viewer123456!";

        if (adminPassword.length() < 12 || ownerPassword.length() < 12 || viewerPassword.length() < 12) {
            throw new IllegalStateException(
                "Each initial account password must be at least 12 characters.");
        }

        createIfMissing(
            adminEmail, "Docurithm Administrator",
            adminPassword, UserRole.ADMIN);

        createIfMissing(
            ownerEmail, "Docurithm Document Owner",
            ownerPassword, UserRole.OWNER);

        createIfMissing(
            viewerEmail, "Docurithm Viewer",
            viewerPassword, UserRole.VIEWER);
    }

    private void createIfMissing(
            String email,
            String fullName,
            String rawPassword,
            UserRole role) {

        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Initial account already exists for role {}", role);
            return;
        }

        AppUser user = new AppUser(
            email,
            fullName,
            passwordEncoder.encode(rawPassword),
            role
        );

        userRepository.save(user);
        log.info("Created initial account for role {}", role);
    }
}