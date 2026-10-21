# TrustVault Threat Model

**Updated:** 2026-10-21

## Assets

- Confidential uploaded documents
- User identities and password hashes
- Per-document permissions and ownership records
- Document activity and audit records
- Oracle database credentials and application secrets
- Authenticated sessions and session cookies
- Server-side file storage and document identifiers
- Audit-chain integrity metadata and the HMAC secret

## Threats and Mitigations

| Threat | Mitigation | Status |
|---|---|---|
| Password exposure | BCrypt password hashing and environment-based secret configuration | Password encoder implemented; repository and deployment secret handling require review. |
| Viewer accesses an administrator endpoint | Server-side Spring Security role authorization and method-level restrictions | Implemented; existing role-authorization tests pass for their tested endpoint boundaries. |
| User accesses another user's private document | Server-side ownership and per-document permission checks | Download authorization implemented and tested for authorized and denied scenarios. Broader endpoint coverage remains necessary. |
| Unauthorized documents appear in listings or search results | Server-side role-specific document queries restrict results by ownership and sharing permissions. | Role-specific listing and search logic implemented; dedicated cross-user visibility tests remain necessary. |
| Access continues after permission revocation | Check the current permission record on every protected request | Manual end-to-end test confirmed that the next download is denied after revocation. Automated coverage remains necessary. |
| Malicious or oversized upload | File-size limits, extension and MIME checks, filename sanitization, and server-generated storage keys | Partially implemented. File-signature validation and additional upload edge-case tests remain. |
| SQL injection | Spring Data JPA operations and parameterized repository queries | Implemented in reviewed repository operations; continue testing query behavior. |
| Unauthorized download attempts go unrecorded | Record successful and denied download events | Implemented and tested for the reviewed download paths. Other denial paths need additional verification. |
| Audit records are modified, deleted, or reordered | HMAC-SHA256 event hashes, hash-linked events, a signed chain-head checkpoint, and a keyed historical-baseline HMAC | HMAC-based audit integrity mechanism implemented. Automated tests for modified records, deleted events, broken event links, and altered checkpoints pass. The historical baseline and key-management limitations remain documented. Further deployment and operational verification is required. |
| Audit-chain verification fails | Verify stored event hashes, chain links, historical baseline and chain head before appending events | A verification failure occurred at event 19 on 2026-10-21. The local development chain was re-baselined; the original cause was not conclusively established. |
| Credentials are committed to Git | Environment variables and `.gitignore`, with repository review for exposed secrets | Initial setup completed; review required. |
| Session hijacking or session misuse | Spring Security sessions, logout handling and CSRF protection | Configuration implemented; session invalidation and production cookie attributes require further verification. |
| Unauthorized permission changes | Server-side ownership checks and permission validation | Grant and revoke behavior manually tested; broader automated tests remain. |
| Audit logging fails or is bypassed | Route audit-writing methods through the integrity service and handle failures explicitly | Centralized audit writing implemented; failure paths and complete event coverage require verification. |

## Document-Upload Threats

| Threat | Current mitigation | Remaining limitation |
|---|---|---|
| Client attempts to choose another owner | Owner is resolved from the authenticated user | Add automated ownership-enforcement tests. |
| Path traversal through an uploaded filename | Original filename is sanitized; a server-generated storage key is used | Test malformed filenames and storage-path containment. |
| Oversized upload | Application-level 10 MB limit | Verify actual configuration and hosting or proxy limits. |
| Unsupported or disguised file types | Extension allowlist and supplied MIME-type checks | These checks do not prove that file contents are safe. Add file-signature validation and tests. |
| Direct public access to stored files | Files are stored outside static resources and accessed through protected endpoints | Verify deployment filesystem permissions and storage configuration. |
| Unauthorized document download | Ownership or explicit VIEW/EDIT permission is checked before returning a file | Existing authorization tests pass for the tested scenarios; broader route coverage is required. |
| Storage-key guessing or exposure | Server-generated identifiers and authorization checks | Verify that knowing a storage key never bypasses authorization. |

## Role-Based Document Visibility

TrustVault uses the following document visibility policy:

- **ADMIN:** Can see documents uploaded by ADMIN users, subject to endpoint authorization.
- **OWNER:** Can see documents they own and documents explicitly shared with them.
- **VIEWER:** Can see only documents explicitly shared with them.

All authenticated roles can search. Search results must remain within the user's authorized document scope.

Administrative audit access does not automatically grant access to every user's confidential document.

## Audit Integrity and Recovery Record

On **2026-10-21**, an audit append failed because `AuditIntegrityService.verifyLocked()` reported:

`Audit event hash verification failed at event 19`

The error occurred while attempting to append a new download event. A timestamp-precision mismatch was considered during troubleshooting, but it was not conclusively established as the root cause. The original failure must not be represented as proof of tampering, nor should a possible implementation defect be represented as confirmed tampering.

For the local development database, the audit chain was re-baselined to recover from the inconsistent state:

- Existing rows in `TV_AUDIT_LOGS` were retained.
- The old `PREVIOUS_HASH` and `EVENT_HASH` values were cleared.
- The chain head was reset to the `GENESIS` and `UNINITIALIZED` state so the application could establish a new baseline.
- The HMAC secret remained configured outside the source code and database.
- The intended new baseline captures the historical audit records at initialization, and subsequent chained events are intended to be verified against that baseline.

**Security limitation:** Re-baselining does not prove that historical audit records were unaltered before the new baseline was calculated. Those records no longer have their previous individual event hashes. The baseline can detect changes after it is established, provided the HMAC secret remains protected and verification is enforced. The local recovery must not be used as a procedure for silently resetting a production audit history.

The re-baseline is a recovery action, not proof that the integrity system has passed all tampering tests.

## Audit Events

The implementation records events for uploads, downloads, metadata changes, deletions, and permission changes in the reviewed paths. Denied downloads and denied edit operations are also recorded in the implemented paths.

Remaining work includes verifying complete event coverage, testing audit-write failure behavior, confirming that all event-writing paths use the integrity service, and testing detection of modified, deleted, reordered, missing-hash and checkpoint-tampering scenarios.

## Accepted Risks

- Historical audit events preceding the new baseline cannot be proven untampered before the baseline was created.
- A database-only attacker who cannot obtain the HMAC secret has a more limited ability to forge valid records, but compromise of both the application environment and database may defeat this protection.
- Local development depends on the developer's computer, credentials, filesystem and configuration. It is not equivalent to production-grade hosting, independent audit storage, high availability or disaster recovery.

## Validation Required

- Test ADMIN, OWNER and VIEWER listing and search boundaries.
- Test cross-user download, editing, deletion and permission changes.
- Test immediate denial after access revocation.
- Test upload size limits, invalid or mismatched file types, malformed filenames and storage-path containment.
- Verify the audit events for upload, download, metadata changes, file replacement, deletion and permission changes, including required denied attempts.
- Test that modified, deleted, reordered or unchained audit events cause integrity verification to fail.
- Test invalid or altered chain-head checkpoints and historical baselines.
- Confirm that an audit-write failure does not silently produce a successful protected operation.
- Review application secrets, session configuration and deployment permissions.

## Status

TrustVault has implemented core document access controls and an HMAC-based audit-integrity mechanism. The development chain required re-baselining on 2026-10-21 after event 19 failed hash verification. Integrity protections must remain marked as **implemented but not fully verified** until post-recovery operation and the tampering-detection tests pass. Documentation of a security control is not itself proof that the control works.
