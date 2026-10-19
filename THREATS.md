# TrustVault Threat Model

**Updated:** 2026-10-19

## Assets

- Confidential uploaded documents
- User identities and password hashes
- Per-document permissions and ownership records
- Document activity and audit records
- Oracle database credentials and application secrets
- Authenticated sessions and session cookies

## Threats and Mitigations

| Threat | Planned or implemented mitigation | Status |
|---|---|---|
| Password exposure | BCrypt password hashing; secrets supplied through environment variables | Initial implementation; verify |
| Viewer accesses an administrator endpoint | Server-side role authorization | Initial implementation; testing required |
| User accesses another user's document | Service-layer, per-document authorization | Pending implementation and testing |
| Access continues after permission revocation | Check current permissions on every protected request; avoid long-lived public links | Pending implementation and testing |
| Malicious or oversized upload | File-size limits, extension and content validation, safe private storage | Partial implementation; verification required |
| SQL injection | Parameterized JPA/repository operations | Apply and test with document features |
| Unauthorized access attempts go unrecorded | Record relevant denied-access events in the audit trail | Pending implementation |
| Audit records are modified or deleted | Hash-linked records, protected integrity checkpoints, and integrity verification | Pending implementation |
| Credentials are committed to Git | Environment variables, `.gitignore`, and secret scanning before pushes | Initial setup completed; review required |
| Session hijacking or session misuse | Secure session configuration, session invalidation on logout, and appropriate cookie protections | Verify implementation |
| Unauthorized permission changes | Server-side owner authorization and validation of permission grant/revoke requests | Verify implementation |
| Audit logging fails or is bypassed | Handle audit-write failures appropriately and test that protected operations cannot silently bypass required audit recording | Pending implementation and testing |

## Accepted Risks

During local development, Oracle and application access depend on the developer's computer and configuration. This setup does not provide production-grade hosting, high availability, disaster recovery, or independent audit-log protection.

## Current Document-Upload Threats

| Threat | Current mitigation | Remaining limitation |
|---|---|---|
| Client attempts to choose another owner | Owner is resolved from the authenticated session | Verify with integration tests |
| Path traversal through an uploaded filename | Server-generated UUID storage keys | Test malformed filenames and storage-path handling |
| Oversized uploads | Reported 10 MB application limit | Confirm actual configuration and hosting/proxy limits |
| Unsupported or disguised file types | Initial extension allowlist | Extension and MIME checks do not establish that file contents are safe |
| Direct public access to stored files | Files are stored outside static resources | Verify filesystem permissions and deployment configuration |
| Unauthorized document download | Download authorization is not implemented yet | Must be implemented and tested before milestone completion |
| Storage-key guessing or exposure | Server-generated storage identifiers and authorization checks | Verify that knowing a storage key never bypasses authorization |

## Validation Required

Before describing these protections as verified, add automated tests for:

- Role boundaries between administrator, owner, and viewer.
- Cross-user document access and ownership enforcement.
- Permission granting, revocation, and immediate access denial after revocation.
- Upload size limits, invalid file types, and malformed filenames.
- Unauthorized download attempts and audit-event recording.
- Audit integrity checks for modified, deleted, and reordered records.
- Session invalidation and unauthorized permission changes.

## Status

This is a working threat-model draft. Controls marked as implemented or partially implemented must be checked against the actual code and tested. Planned controls must remain marked as pending until implementation and verification are complete.