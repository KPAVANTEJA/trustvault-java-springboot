# API Access Matrix

Updated: 2026-10-19

All paths are relative to the application root.

| Method | Endpoint | Access requirement | Purpose |
|---|---|---|---|
| GET | `/index.html` | Public | Landing page |
| GET | `/login` | Public | Spring Security sign-in page |
| GET | `/api/auth/me` | Authenticated user | Current identity and roles |
| GET | `/api/admin/access-check` | ADMIN | Initial role-gate verification |
| GET | `/api/owner/access-check` | ADMIN or OWNER | Initial role-gate verification |
| GET | `/api/viewer/access-check` | Any authenticated role | Initial role-gate verification |

Spring Security's default CSRF protection remains enabled. The standard
form-login flow should be used for login; logout uses Spring Security's
logout endpoint.

The access-check routes demonstrate role boundaries; they do not manage
documents. Document endpoints will be documented here when implemented,
including required role, document-level permission, request fields,
response fields, and denial behavior.

Do not treat an authenticated session or a role alone as permission to
read any arbitrary document.