
package com.trustvault.security;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;

public class LiveUserAccountFilter extends OncePerRequestFilter {

    private final AppUserRepository userRepository;

    public LiveUserAccountFilter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        AppUser user = userRepository
                .findByEmailIgnoreCase(authentication.getName())
                .orElse(null);

        boolean accountValid = user != null
                && user.isEnabled()
                && hasCurrentRole(authentication, user);

        if (accountValid) {
            filterChain.doFilter(request, response);
            return;
        }

        // Invalidate the existing session and its authentication.
        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // APIs receive an authentication failure; browser requests
        // are redirected to the login page.
        String apiPrefix = request.getContextPath() + "/api/";

        if (request.getRequestURI().startsWith(apiPrefix)) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Account is disabled or its role changed. Sign in again.");
        } else {
            response.sendRedirect(
                    request.getContextPath() + "/login.html?accountChanged");
        }
    }

    private boolean hasCurrentRole(
            Authentication authentication,
            AppUser user) {

        if (user.getRole() == null) {
            return false;
        }

        String expectedAuthority = "ROLE_" + user.getRole().name();

        Set<String> currentRoleAuthorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        return currentRoleAuthorities.equals(Set.of(expectedAuthority));
    }
}
