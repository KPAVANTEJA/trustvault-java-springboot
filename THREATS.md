# TrustVault Threat Model

Updated: 2026-10-19

## Assets

- Confidential uploaded documents
- User identities and password hashes
- Per-document permissions
- Document activity and audit records
- Oracle database credentials

## Threats and Mitigations

| Threat | Planned or implemented mitigation | Status |
|---|---|---|
| Password exposure | BCrypt hashes; secrets in environment variables | Initial implementation; verify |
| Viewer reaches administrator endpoint | Server-side role authorization | Initial implementation; test required |
| User accesses another user's document | Per-document authorization in the service layer | Not implemented yet |
| Access continues after revocation | Check current grants on each request; avoid long-lived public links | Not implemented yet |
| Malicious or oversized upload | Size limits, type validation, safe storage outside public web root | Not implemented yet |
| SQL injection | Parameterized JPA/repository operations | Apply and test with document features |
| Unauthorized attempts go unrecorded | Record and verify denied-access events | Not implemented yet |
| Audit records are rewritten | Hash-linked records with a protected integrity checkpoint | Not implemented yet |
| Credentials committed to Git | Environment variables and `.gitignore` | Initial setup completed; review before pushes |

## Accepted Risks

During local development, Oracle and application access depend on the
developer's computer and configuration. This is not a production hosting
or disaster-recovery arrangement.

## Validation Required

The project must include automated tests for role boundaries, cross-user
document access, revocation, and audit integrity before these protections
are described as verified.