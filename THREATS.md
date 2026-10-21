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

## Threats and Mitigations

| Threat | Mitigation | Status |
|---|---|---|
| Password exposure | BCrypt password hashing is configured. Sensitive configuration should be supplied through environment variables rather than committed to source control. | Password encoder implemented; secret handling and repository contents require review. |
| Viewer accesses an administrator endpoint | Server-side Spring Security role authorization and method-level authorization. | Implemented. Existing role-authorization tests pass for the tested endpoint boundaries. |
| User accesses another user's private document | Download authorization checks document ownership or an explicit document permission. Unauthorized document downloads return 404. | Implemented and covered by passing authorized and unauthorized download tests. |
| Unauthorized documents appear in listings or search results | Server-side role-specific document queries restrict results by ownership and sharing permissions. | Role-specific listing and search logic implemented; dedicated cross-user visibility tests remain necessary. |
| Access continues after permission revocation | Permission records are checked on each protected download request. | Manual end-to-end verification confirmed that a viewer loses download access after the owner revokes permission. Add automated revocation tests. |
| Malicious or oversized upload | Application-level file-size limits, extension validation, and server-generated storage identifiers are used. | Partial implementation. Verify effective size limits, file-content validation, and malformed filename handling. |
| SQL injection | Repository operations use Spring Data JPA derived queries and parameterized query parameters. | Implemented in the reviewed repository operations; continue testing query behavior. |
| Unauthorized download attempts go unrecorded | Denied document downloads are recorded through the audit service, including an explicit denied outcome. | Implemented and covered by a passing test for unauthorized download auditing. Other denied operations require separate review. |
| Audit records are modified, deleted, or reordered | Hash-linked records, integrity checkpoints, and verification have been identified as possible controls. | Pending implementation. Audit logs are not yet proven tamper-evident. |
| Credentials are committed to Git | Environment-based configuration and `.gitignore` are part of the development setup. Repository and commit history should be reviewed for exposed secrets. | Initial setup completed; review required. |
| Session hijacking or session misuse | Spring Security manages authentication and logout, and CSRF protection is configured. | Partial security configuration implemented; session invalidation and production cookie attributes require verification. |
| Unauthorized permission grants or revocations | Permission service checks document ownership and validates grant/revoke operations. | Implemented. Sharing and revocation have been manually verified; automated permission-service tests remain recommended. |
| Audit logging fails or is bypassed | Denied download events use a separate transaction to improve the chance that denial records persist. | Partial implementation. Failure handling and enforcement of required audit recording need further testing. |

## Document-Upload Threats

| Threat | Current mitigation | Remaining limitation |
|---|---|---|
| Client attempts to choose another document owner | Document ownership is resolved from the authenticated user rather than trusted client-supplied ownership. | Add an automated test proving that a client cannot assign a document to another user. |
| Path traversal through an uploaded filename | Server-generated UUID storage identifiers are used instead of using the original filename as the storage key. | Test malformed filenames and verify that resolved storage paths remain within the intended storage directory. |
| Oversized upload | An application upload limit of 10 MB has been reported. | Confirm the effective multipart configuration and any hosting or reverse-proxy limits. |
| Unsupported or disguised file types | An extension allowlist is used. | Extension and MIME checks alone do not prove that file contents are safe. Test invalid, mismatched, and disguised file types. |
| Direct public access to stored files | Uploaded documents are stored outside the application's static-resource directory and are accessed through protected endpoints. | Verify filesystem permissions and storage configuration in the deployment environment. |
| Unauthorized document download | The download endpoint checks ownership or an explicit VIEW/EDIT permission before returning the file. | Authorized and unauthorized download tests pass. Continue testing across roles and after permission changes. |
| Storage-key guessing or exposure | Server-generated storage identifiers are used, and downloads require authorization. | Verify that knowledge of a storage identifier never bypasses access checks. |

## Role-Based Document Visibility

TrustVault follows these document visibility rules:

- **ADMIN:** Can access documents uploaded by ADMIN users, subject to the implemented endpoint permissions.
- **OWNER:** Can access documents they own and documents explicitly shared with them.
- **VIEWER:** Can access only documents explicitly shared with them.

All authenticated users may use search, but the search results must follow the same visibility rules as the document listing. Search must not disclose the existence or metadata of unauthorized documents.

Administrative audit access does not automatically grant access to every user's confidential document.

## Audit and Permission Events

The current implementation supports audit events for document downloads, denied download attempts, deletions, and permission changes.

The administrative audit endpoint is restricted to ADMIN users and supports paginated results.

Remaining work includes testing permission-change auditing, reviewing audit-write failure behavior, and implementing and validating tamper-evident audit records. Recording events in a database does not, by itself, prevent a privileged database user from altering them.

## Accepted Risks

During local development, Oracle and application access depend on the developer's computer, credentials, filesystem, and configuration. The local setup does not provide production-grade high availability, disaster recovery, independent audit-log protection, or verified production security settings.

These limitations must be reassessed before deployment.

## Validation Required

Before marking the remaining protections as verified, complete the following:

- Add automated tests for ADMIN, OWNER, and VIEWER document listing and search boundaries.
- Test cross-user download denial and access to documents with valid VIEW and EDIT permissions.
- Add automated tests for permission granting, revocation, and immediate access denial after revocation.
- Test upload size limits, disallowed file types, mismatched content types, malformed filenames, and storage-path containment.
- Verify that denied operations generate the expected audit records.
- Test audit integrity detection for modified, deleted, and reordered records after an integrity mechanism is implemented.
- Verify session invalidation, production cookie settings, and protection against unauthorized permission changes.
- Review repository history and deployment configuration for leaked credentials or secrets.

## Status

This threat model distinguishes implemented controls from tested behavior and unfinished work. A feature must not be labelled verified merely because its code exists. Update the status and validation evidence as new tests pass and security controls are implemented.
