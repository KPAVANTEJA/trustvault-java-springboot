package com.trustvault.auth;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trustvault.user.AppUserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class AuthController {

    private final AppUserRepository userRepository;

    public AuthController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/api/auth/me")
    public Map<String, Object> currentUser(Authentication authentication) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("email", authentication.getName());
        result.put("roles", authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .sorted()
                .toList());

        userRepository.findByEmailIgnoreCase(authentication.getName()).ifPresent(user -> {
            result.put("fullName", user.getFullName());
            result.put("role", user.getRole().name());
            result.put("id", user.getId());
        });

        return result;
    }

    @GetMapping("/logout")
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        response.sendRedirect("/login.html?logout");
    }
}