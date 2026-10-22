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

## Authorization Test Coverage — 2026-10-22

The following tests have been implemented and reported passing during development. Mock-based controller tests and service tests verify specific authorization decisions; database-backed tests verify document visibility queries. These test types provide different levels of evidence and are not interchangeable.

| Threat | Mitigation | Verification evidence |
|---|---|---|
| IDOR/BOLA: an OWNER accesses another owner's document | Server-side ownership validation | `ownerCannotDeleteAnotherUsersDocument`, `ownerCannotGrantPermissionOnAnotherUsersDocument`, `ownerCannotRevokePermissionOnAnotherUsersDocument` |
| VIEWER attempts metadata modification | Explicit document-level EDIT permission check | `viewerCannotEditAnotherUsersDocument` |
| VIEW permission is mistaken for EDIT | Separate VIEW and EDIT permission checks | `viewerWithViewPermissionCannotEditDocument` |
| Unauthorized user replaces file contents | Server-side authorization before storing the replacement | `viewerCannotReplaceAnotherUsersDocumentContent` |
| VIEW-only user replaces file contents | Require explicit EDIT permission | `viewerWithViewPermissionCannotReplaceDocumentContent` |
| Authorized EDIT user is denied incorrectly | Permit explicitly granted EDIT permission | `userWithEditPermissionCanUpdateDocumentMetadata`, `userWithEditPermissionCanReplaceDocumentContent` |
| VIEWER uploads a document | Server-side role restriction | `viewerCannotUploadDocuments` |
| Private documents leak through listing or search | Role-specific repository queries and explicit sharing | `DocumentVisibilityIntegrationTests` |
| Revoked permission remains effective | Recheck current permission on each protected request | `revokedPermissionImmediatelyPreventsNextDownload` and prior manual revocation verification |
| Unauthorized permission changes | Check document ownership in the permission service | `DocumentPermissionAuthorizationTests` |
| Permission changes lack audit records | Record grant, update, revoke, and denied attempts | `DocumentPermissionAuditIntegrationTests`, `DocumentPermissionFailureAuditIntegrationTests` |
| VIEWER invokes owner/admin permission endpoints directly | Spring Security method authorization | `DocumentPermissionControllerAuthorizationTests` |
| Denied permission-endpoint attempts lack audit records | Access-denied handler records grant, revoke, and permission-list denials for identifiable users and documents | Task 7Q: manual verification confirmed the expected denied events |
| Audit history is modified or corrupted | HMAC-SHA256 event hashes, linked hashes, and a signed chain-head checkpoint | `AuditIntegrityTamperingTests`: modified event, deleted event, broken chain link, and modified checkpoint scenarios |

### Evidence limitations

- Mock-based tests demonstrate the behavior of the code paths represented by their mocks. They do not, by themselves, prove database query correctness or production behavior.
- Database-backed visibility tests verify the selected ADMIN, OWNER, and VIEWER visibility policy against the test database.
- Revocation testing covers the tested request paths. Download authorization must continue to check current permission state rather than trust cached grants.
- The denied permission-endpoint handler requires an authenticated user and an identifiable existing document to create a document-linked audit record. Requests for nonexistent documents and unauthenticated requests have separate auditing limitations.
- Successful and denied permission changes have integration tests for persisted audit events and integrity hashes.
- The audit chain was re-baselined in the local development database after verification failed at event 19 on 2026-10-21. Historical events cannot be claimed to have individual hash protection before that re-baseline.
- Automated test success does not demonstrate resistance to every possible attack. Production configuration, storage permissions, deployment behavior, and audit integrity still require verification.

## Permission and visibility policy

- **ADMIN:** The current document listing/search policy returns documents owned by ADMIN users. ADMIN access to the audit endpoints does not automatically imply access to all private document contents.
- **OWNER:** Can access documents they own and documents explicitly shared with them, according to the current owner/shared query. Owners manage grants for documents they own.
- **VIEWER / recipient:** Can access only documents explicitly shared with them. `VIEW` permits read/download. `EDIT` permits the implemented metadata update and file-content replacement operations, but not permission administration or deletion.
- **Least privilege:** UI hiding is not an authorization control. Every protected API request must enforce authorization on the server.
- **Revocation:** Protected download paths query current permission state on each request. A file already downloaded cannot be recalled.

## Threat register

| ID | Threat / attack | Control implemented or required | Current evidence and residual risk |
|---|---|---|---|
| T-01 | IDOR: user changes a document ID to reach another user's document | Resolve document server-side, compare owner or explicit grant, deny by default | Authorization tests cover private download, edit, replacement, deletion, permission grants/revokes. Continue auditing every controller route and HTTP method. |
| T-02 | VIEW is mistakenly treated as EDIT | Check explicit `PermissionType.EDIT` for metadata/content changes | Tests reported passing: `viewerWithViewPermissionCannotEditDocument`, `viewerWithViewPermissionCannotReplaceDocumentContent`; positive EDIT tests also reported passing. |
| T-03 | Revoked permission remains effective | Look up current grant on each protected request; no direct public file links | `revokedPermissionImmediatelyPreventsNextDownload` and prior manual revocation verification reported passing. The automated test simulates the permission repository returning the revoked state. |
| T-04 | VIEWER uploads or manages permission grants | Role/method security and service authorization | `viewerCannotUploadDocuments` and `DocumentPermissionControllerAuthorizationTests` reported passing. The latter tests deny permission grant/revoke/list at the controller boundary. |
| T-05 | OWNER changes grants on another owner's document | Enforce ownership in `DocumentPermissionService` | `DocumentPermissionAuthorizationTests` covers grant/revoke denial; successful and denied audit integration tests reported passing. |
| T-06 | Search or listing leaks private document metadata | Role-specific queries: VIEWER shared-only, OWNER owned-or-shared, ADMIN according to the documented ADMIN-owned-document policy | `DocumentVisibilityIntegrationTests` reported passing against the H2 test database. This does not prove all future query paths are safe. |
| T-07 | CSRF on state-changing endpoints | Spring Security CSRF enabled; browser requests send the token | Configuration and tests use CSRF tokens for POST/PUT/DELETE. Verify all actual frontend flows and production cookie settings. |
| T-08 | Uploaded file is empty, oversized, has an unsafe filename, or uses an unsupported extension/MIME type | Reject empty files; 10 MB size limit; filename normalization; allowlist PDF, TXT, PNG, JPG, JPEG; compare supplied MIME type | These checks are implemented in reviewed upload/replacement paths. **File-signature/content validation is still pending (Task 8).** MIME and extension values can be spoofed; these checks alone do not establish safe file content. No malware scanning is claimed. |
| T-09 | Storage path traversal or direct public file access | Server-generated storage key, private storage abstraction, downloads through authorization-checked API | Existing implementation uses server-side storage keys. Deployment storage permissions and direct-access denial still need environment verification. |
| T-10 | Upload or metadata update isn't audited | Write audit events for successful operations in the service/controller paths | Successful upload audit and metadata update audit were manually/integration verified in the reviewed paths. Failure coverage is not yet exhaustive. |
| T-11 | Download or edit denial isn't audited | Persist denied outcomes through audit service and HMAC append path | Denied download/edit tests were reported passing. Other exception paths and audit-write failure behavior need complete coverage. |
| T-12 | Permission change or denied attempt isn't audited | Record `PERMISSION_GRANT`, `PERMISSION_UPDATE`, `PERMISSION_REVOKE`, and denied operations | `DocumentPermissionAuditIntegrationTests` and `DocumentPermissionFailureAuditIntegrationTests` reported passing; Task 7Q denial events were manually confirmed through the admin audit view. Verify names/outcomes remain consistent. |
| T-13 | Audit record is altered, removed or reordered | HMAC-SHA256 event hash; previous-hash linkage; keyed historical baseline; signed chain-head checkpoint; verifier before append | Tests reported passing for modified event data, deleted event, broken link, and altered checkpoint. More cases—concurrent writes, bypass insertions, key rotation, and recovery failure—remain. |
| T-14 | Audit chain history has a gap in trust | Keep HMAC key outside the database; preserve checkpoint evidence; verify before append | On 2026-10-21, verification failed at event 19. The **local development chain was re-baselined**: historical rows were retained but prior per-event hashes were cleared, and a new baseline was established. This cannot prove old rows were untampered before re-baselining. Never silently repeat this procedure in production. |
| T-15 | Database-only attacker forges all audit data | Keep HMAC key outside database and restrict DB roles | An attacker with database write access but not the HMAC key has a narrower ability to forge records. An attacker controlling both the application environment/key and database may defeat this control. The checkpoint is stored in the same database and is not an independent off-host checkpoint. |
| T-16 | Audit events are written concurrently against the same previous hash | Serialize chain appends with a pessimistic lock on the chain-head row and transaction control | Locking strategy is implemented. A dedicated concurrent-write/race test is still needed. |
| T-17 | Secret committed to source control | Environment variable for the HMAC secret; test-only key is restricted to test configuration | Production key must never be committed or shared. Review current Git history before public submission. Rotate any exposed production secret. |
| T-18 | Session/credential misconfiguration | Spring Security session login, BCrypt password encoder, CSRF enabled | Local configuration has been exercised. HTTPS, `Secure`/`HttpOnly`/`SameSite` cookie policy, session timeout, fixation/logout behavior and deployed configuration need final verification. |
| T-19 | SQL injection through search parameters | Spring Data JPA and parameterized repository queries | Reviewed query paths use JPA/repository methods. Test adversarial input and avoid string-concatenated SQL. |
| T-20 | Stored XSS via title, category or filename | Render values as text, not executable HTML; validate input | Frontend output contexts and CSP remain to be reviewed; do not claim XSS protection until verified. |
| T-21 | File replacement leaves stale/orphaned bytes after DB failure | Store new file, update transactionally, remove old/new file only after appropriate transaction completion | Replacement path implements deferred cleanup hooks; test storage/DB/audit failures and confirm old file remains usable after rollback. Filesystem and Oracle do not share a true transaction. |
| T-22 | Admin privileges accidentally grant document-content access | Separate audit/admin access from per-document ownership/grant checks | ADMIN visibility policy is documented above; inspect each controller/service path for exceptions. |
| T-23 | Audit-write failure silently allows a sensitive action | Couple successful operation and audit write transactionally; denied path must fail visibly if audit persistence fails | Some success paths share the transaction; denied permission endpoint handler returns an error if its audit write fails. All operation failure paths still require systematic testing. |
| T-24 | Deployment leaks files or loses them on restart | HTTPS, private persistent storage, correct filesystem permissions/backups | Live deployment and persistence are not yet confirmed in this record. Treat deployment hardening as incomplete until verified. |

## Audit recovery disclosure

On **2026-10-21**, `AuditIntegrityService.verifyLocked()` raised `Audit event hash verification failed at event 19` while processing a new download audit event. Timestamp precision was considered as a possible cause but was **not conclusively established**. The local development chain was re-baselined after retaining existing `TV_AUDIT_LOGS` rows and clearing their old `PREVIOUS_HASH`/`EVENT_HASH` values; the chain-head row was reset for initialization. Subsequent chain verification and tampering tests were reported passing.

This recovery is disclosed because old rows cannot be represented as individually hash-protected before the new baseline. The baseline only detects changes after it is established, subject to secret protection and enforcement. Do not use development re-baselining as a routine or silent production recovery.

## Verification record (2026-10-22)

The developer reported that `mvn test` passed after correcting Mockito strict-stubbing issues. Earlier test groups also reported passing:

- `DocumentDownloadAuthorizationTests` (download, edit, replacement, upload, listing/search isolation, delete denial, immediate revocation scenarios).
- `DocumentVisibilityIntegrationTests` (H2 repository-backed visibility for VIEWER, OWNER, ADMIN policy).
- `DocumentPermissionAuthorizationTests` (unauthorized grant/revoke and successful grant/revoke service tests).
- `DocumentPermissionAuditIntegrationTests` (successful permission grant/revoke audit persistence and hashes).
- `DocumentPermissionFailureAuditIntegrationTests` (denied grant/revoke audit events with hashes, no permission mutation, chain verification).
- `DocumentPermissionControllerAuthorizationTests` (VIEWER receives 403 for grant/revoke/list endpoints).
- `AuditIntegrityServiceTests` and `AuditIntegrityTamperingTests` (verification plus four specified tampering scenarios).

This is a record of developer-reported test results in the project conversation; re-run the complete suite from a clean clone before submission.

## Accepted residual risks / not claimed

- No malware scanning or content disarm/reconstruction is implemented/verified.
- File-signature validation remains Task 8 and is not complete as of this update.
- No independently stored/off-host audit checkpoint or external append-only audit sink is verified.
- The historical chain cannot prove records were intact before the 2026-10-21 development re-baseline.
- Full compromise of the application environment and HMAC secret plus database may defeat the audit scheme.
- No production deployment/HTTPS/persistent-storage check is recorded as complete yet.
- A file already downloaded or copied by a recipient cannot be remotely recalled by revocation.
- The current audit schema ties records to a document and actor; requests lacking an existing document or authenticated actor may not produce a document-linked event.
- A passing test suite is not proof against all attack classes; testing, deployment review and threat-model updates continue until submission.

### Current verification status

Record the actual latest test-suite result here before submission. Individual tests reported passing earlier must not be treated as proof that the complete suite currently passes after later edits.

## Status

TrustVault has implemented core document access controls and an HMAC-based audit-integrity mechanism. The development chain required re-baselining on 2026-10-21 after event 19 failed hash verification. Integrity protections must remain marked as **implemented but not fully verified** until post-recovery operation and the tampering-detection tests pass. Documentation of a security control is not itself proof that the control works.

## File Content Integrity — SHA-256

### Threat
An attacker or unexpected filesystem change may modify a stored
document without updating its database metadata.

### Mitigation implemented
- Calculate a SHA-256 fingerprint when a document is uploaded.
- Store the fingerprint in `TV_DOCUMENTS.CONTENT_SHA256`.
- Recalculate and save the fingerprint when document content is replaced.
- Compare the stored file's current fingerprint with the saved fingerprint
  before returning a download.
- Reject downloads when the fingerprint is missing or does not match.
- Record integrity failures through the download-failure audit path.

### Verification
- `DocumentDownloadAuthorizationTests#downloadRejectsFileWhoseContentsHaveChanged`
  passed.
- The complete `mvn test` suite passed on 2026-10-22.
- The Oracle `CONTENT_SHA256` column was added.
- Fingerprints were generated from the current stored files to establish
  a baseline for existing documents.

### Residual risks and limitations
- The baseline for existing documents reflects their contents at the time
  the hashes were calculated. It does not prove historical integrity.
- SHA-256 checks detect content differences but do not scan for malware.
- The fingerprint is stored in the database alongside document metadata.
  An attacker able to modify both the file and its fingerprint may bypass
  this check.
- A file could theoretically change after verification but before it has
  been fully delivered to the user.