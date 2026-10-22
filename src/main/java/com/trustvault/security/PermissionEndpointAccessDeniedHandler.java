package com.trustvault.security;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.trustvault.audit.AuditLogService;
import com.trustvault.document.Document;
import com.trustvault.document.DocumentRepository;
import com.trustvault.user.AppUser;
import com.trustvault.user.AppUserRepository;

@Component
public class PermissionEndpointAccessDeniedHandler
        implements AccessDeniedHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PermissionEndpointAccessDeniedHandler.class);

    private static final Pattern PERMISSION_PATH = Pattern.compile(
            "^/api/documents/(\\d+)/permissions(?:/\\d+)?/?$");

    private final DocumentRepository documentRepository;
    private final AppUserRepository userRepository;
    private final AuditLogService auditLogService;

    public PermissionEndpointAccessDeniedHandler(
            DocumentRepository documentRepository,
            AppUserRepository userRepository,
            AuditLogService auditLogService) {

        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {

        String action = resolveAction(request);

        if (action != null) {
            try {
                recordDeniedAttempt(request, action);
            } catch (RuntimeException auditFailure) {
                log.error(
                        "Unable to record denied permission-endpoint request",
                        auditFailure);

                response.setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"Request denied; security audit "
                                + "could not be recorded.\"}");
                return;
            }
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"Forbidden\"}");
    }

    private String resolveAction(HttpServletRequest request) {
        String path = request.getRequestURI()
                .substring(request.getContextPath().length());

        Matcher matcher = PERMISSION_PATH.matcher(path);

        if (!matcher.matches()) {
            return null;
        }

        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method)
                && !path.matches(
                        "^/api/documents/\\d+/permissions/\\d+/?$")) {
            return "PERMISSION_GRANT";
        }

        if ("DELETE".equalsIgnoreCase(method)
                && path.matches(
                        "^/api/documents/\\d+/permissions/\\d+/?$")) {
            return "PERMISSION_REVOKE";
        }

        if ("GET".equalsIgnoreCase(method)
                && !path.matches(
                        "^/api/documents/\\d+/permissions/\\d+/?$")) {
            return "PERMISSION_LIST";
        }

        return null;
    }

    private void recordDeniedAttempt(
            HttpServletRequest request,
            String action) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return;
        }

        String path = request.getRequestURI()
                .substring(request.getContextPath().length());

        Matcher matcher = PERMISSION_PATH.matcher(path);

        if (!matcher.matches()) {
            return;
        }

        Long documentId = Long.valueOf(matcher.group(1));

        Optional<Document> documentOptional =
                documentRepository.findById(documentId);

        Optional<AppUser> userOptional =
                userRepository.findByEmailIgnoreCase(
                        authentication.getName());

        // The current audit schema requires a real document and user.
        // If either is absent, a document-linked denial cannot be recorded.
        if (documentOptional.isEmpty() || userOptional.isEmpty()) {
            log.warn(
                    "Permission request denied but could not be linked "
                            + "to an existing document and user");
            return;
        }

        auditLogService.recordPermissionChangeFailure(
                documentOptional.get(),
                userOptional.get(),
                action,
                "DENIED");
    }
}