package com.trustvault.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import jakarta.servlet.FilterChain;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;
import com.trustvault.user.UserRole;

class LiveUserAccountFilterTests {

    private AppUserRepository userRepository;
    private LiveUserAccountFilter filter;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        filter = new LiveUserAccountFilter(userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledAccountInvalidatesSessionAndRejectsApiRequest()
            throws Exception {

        authenticateAsViewer();

        AppUser viewer = mock(AppUser.class);
        when(viewer.isEnabled()).thenReturn(false);

        when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
                .thenReturn(Optional.of(viewer));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/documents");

        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(session.isInvalid());

        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void roleChangeInvalidatesSessionAndRejectsApiRequest()
            throws Exception {

        authenticateAsViewer();

        AppUser viewer = mock(AppUser.class);
        when(viewer.isEnabled()).thenReturn(true);
        when(viewer.getRole()).thenReturn(UserRole.OWNER);

        when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
                .thenReturn(Optional.of(viewer));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/documents");

        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(session.isInvalid());

        verify(chain, never()).doFilter(request, response);
    }
    
    @Test
    void missingAccountInvalidatesSessionAndRejectsApiRequest()
            throws Exception {

        authenticateAsViewer();

        when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
                .thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/documents");

        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(session.isInvalid());

        verify(chain, never()).doFilter(request, response);
    }
    
    @Test
    void disabledAccountRedirectsBrowserToLogin() throws Exception {
        authenticateAsViewer();

        AppUser viewer = mock(AppUser.class);
        when(viewer.isEnabled()).thenReturn(false);

        when(userRepository.findByEmailIgnoreCase("viewer@example.com"))
                .thenReturn(Optional.of(viewer));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard.html");

        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(302, response.getStatus());
        assertEquals("/login.html?accountChanged", response.getRedirectedUrl());
        assertTrue(session.isInvalid());

        verify(chain, never()).doFilter(request, response);
    }
    
    @Test
    void unauthenticatedRequestContinuesThroughFilter() throws Exception {
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/documents");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(userRepository);
    }

    private void authenticateAsViewer() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "viewer@example.com",
                "test-password",
                List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }
}