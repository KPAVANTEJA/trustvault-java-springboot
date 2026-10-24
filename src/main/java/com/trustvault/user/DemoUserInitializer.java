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

        String[] keys = {
            "TRUSTVAULT_ADMIN_EMAIL",
            "TRUSTVAULT_ADMIN_PASSWORD",
            "TRUSTVAULT_OWNER_EMAIL",
            "TRUSTVAULT_OWNER_PASSWORD",
            "TRUSTVAULT_VIEWER_EMAIL",
            "TRUSTVAULT_VIEWER_PASSWORD"
        };

        String[] values = Arrays.stream(keys)
            .map(environment::getProperty)
            .toArray(String[]::new);

        boolean noneConfigured = Arrays.stream(values)
            .allMatch(value -> value == null || value.isBlank());

        if (noneConfigured) {
            log.warn("Initial accounts were not created. Configure the "
                + "six TRUSTVAULT_* environment variables.");
            return;
        }

        boolean incomplete = Arrays.stream(values)
            .anyMatch(value -> value == null || value.isBlank());

        if (incomplete) {
            throw new IllegalStateException(
                "Configure all six TRUSTVAULT_* initial-account "
                + "environment variables.");
        }

        for (int i = 1; i < values.length; i += 2) {
            if (values[i].length() < 12) {
                throw new IllegalStateException(
                    "Each initial account password must be at least "
                    + "12 characters.");
            }
        }

        createIfMissing(
            values[0], "Docurithm Administrator",
            values[1], UserRole.ADMIN);

        createIfMissing(
            values[2], "Docurithm Document Owner",
            values[3], UserRole.OWNER);

        createIfMissing(
            values[4], "Docurithm Viewer",
            values[5], UserRole.VIEWER);
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