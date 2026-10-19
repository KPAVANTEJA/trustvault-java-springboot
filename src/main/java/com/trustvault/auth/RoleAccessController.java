package com.trustvault.auth;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoleAccessController {

    @GetMapping("/api/admin/access-check")
    public Map<String, String> adminAccess() {
        return Map.of("status", "granted", "scope", "ADMIN");
    }

    @GetMapping("/api/owner/access-check")
    public Map<String, String> ownerAccess() {
        return Map.of("status", "granted", "scope", "OWNER");
    }

    @GetMapping("/api/viewer/access-check")
    public Map<String, String> viewerAccess() {
        return Map.of("status", "granted", "scope", "VIEWER");
    }
}