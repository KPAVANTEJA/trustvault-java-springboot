# AI Prompt and Correction Log

This file records AI-assisted work on TrustVault, including prompt summaries, AI assistance, mistakes or limitations identified, corrective actions, verification evidence, and remaining limitations. Prompt summaries describe the work that occurred and are not necessarily verbatim prompts.

The log distinguishes proposed, implemented, manually tested, and automated-test-verified work. A successful build or test run is evidence only for the behavior covered by that run; it is not proof of production readiness. Evidence commit links are intentionally left pending so they can be added from actual repository history. No commit hashes or GitHub URLs are inferred here.


## Entry 1 — 2026-10-19 — Application Startup

**Prompt summary:** Help start the Spring Boot TrustVault application.

**AI assistance summary:** Initial troubleshooting focused on running the application before its datasource configuration had been fully confirmed.

**Issue identified:** The application did not have a usable datasource URL and failed at startup.

**Corrective action:** Configure Oracle connection settings through environment variables, confirm the Oracle service name, and verify startup against the intended schema.

**Verification status:** The application was subsequently reported to start successfully against the clean test schema using the Oracle `XE` service. The original `MYDATABASE` schema was preserved. This startup verification does not by itself establish that a production datasource is configured.

**Evidence commit:** Pending — add the actual commit link after reviewing Git history.

## Entry 2 — 2026-10-19 — Role-Based Authorization

**Prompt summary:** Protect administrator, owner, and viewer operations.

**AI assistance summary:** Spring Security URL rules and method-level authorization were developed for role-specific endpoints.

**Limitation identified:** Role checks on endpoints do not, by themselves, establish whether a user has permission to access a specific document.

**Corrective action:** Retain server-side role checks and add document-level ownership and permission checks where document content or permissions are accessed.

**Verification status:** Five role-authorization tests were reported passing for the endpoint boundaries they cover. These tests are distinct from tests of document-specific grants and revocations.

**Evidence commit:** Pending.

## Entry 3 — 2026-10-19 — Audit Integrity

**Prompt summary:** Design an audit trail that can detect tampering.

**AI assistance summary:** A hash-linked audit sequence and integrity verification were developed as the foundation of a tamper-evident audit trail.

**Limitation identified:** A hash chain alone is not sufficient against an attacker who can rewrite all records and recompute their hashes. The chain also depends on the integrity of its key, chain-head/checkpoint data, and verification process.

**Corrective action:** Implement HMAC-protected event hashes and a chain-head/checkpoint mechanism, expose administrative verification, and add tests for record and metadata tampering.

**Verification status:** Audit verification and multiple tampering-test scenarios were subsequently reported passing. A development-chain re-baseline is documented in Entry 12: it retained audit rows but cleared the historical hash links, so it cannot establish the integrity of those records before the new baseline. Production key management and the production database have not been independently verified.

**Evidence commit:** Pending.

## Entry 4 — 2026-10-19 — Document Upload and Private Storage

**Prompt summary:** Implement document uploads, metadata persistence, ownership association, and file storage.

**AI assistance summary:** Development covered the upload endpoint, metadata persistence, authenticated ownership, server-generated storage identifiers, file validation, and file-storage handling.

**Security consideration identified:** Client-provided filenames or metadata must not let a caller choose an arbitrary filesystem path or claim another user's ownership. File-size and content-validation rules must be enforced by the server.

**Corrective action:** Use the authenticated user to establish document ownership, use server-generated UUID storage keys, and enforce the configured upload-size limit and file validation.

**Verification status:** Upload functionality was manually exercised. SHA-256 fingerprinting of uploaded files was added later and is documented in Entry 15. Automated validation coverage is limited to the tests actually present in the suite.

**Evidence commit:** Pending.

## Entry 5 — 2026-10-19 — Document Listing and Access Boundaries

**Prompt summary:** Restrict document listings to records visible to the authenticated user.

**AI assistance summary:** Role-aware repository queries and controller logic were developed for `ADMIN`, `OWNER`, and `VIEWER` users.

**Limitation identified:** A general listing query can expose document metadata beyond the authenticated user's intended visibility scope. Listing visibility must remain consistent with the authorization enforced by content endpoints.

**Corrective action:** Keep `VIEWER` listing restricted to explicitly shared documents and `OWNER` listing restricted to owned or shared documents. The admin-list behavior was refined later: the final admin list and search use the signed-in admin's owned-or-shared query so a document explicitly shared with that admin can appear without returning every document in the vault. See Entries 16 and 21.

**Verification status:** The role-specific code was updated and the project owner reported the relevant fixes and tests complete. Dedicated cross-user listing tests in the final repository are the evidence needed to prove every role boundary.

**Evidence commit:** Pending.

## Entry 6 — 2026-10-20 — Per-Document Sharing and Revocation

**Prompt summary:** Allow document owners to grant and revoke `VIEW` or `EDIT` access for named users.

**AI assistance summary:** Development covered the permission service, permission endpoints, recipient validation, ownership checks, and dashboard sharing controls.

**Limitation identified:** Authentication alone does not authorize a user to change sharing permissions for another user's document.

**Corrective action:** Enforce server-side authorization when granting, listing, and revoking permissions. Validate recipient and permission information on the server and record permission changes in the audit chain.

**Verification status:** Manual end-to-end testing was reported to confirm that a `VIEW` grant allowed a recipient to download a document and that revocation prevented the recipient's next download. Permission endpoints and associated audit events were also exercised manually.

**Evidence commit:** Pending.

## Entry 7 — 2026-10-20 — Authorized and Unauthorized Document Downloads

**Prompt summary:** Implement protected document downloads and test both authorized and denied access.

**AI assistance summary:** The download endpoint checks document ownership or explicit `VIEW`/`EDIT` permission before returning content. Denied download attempts are recorded.

**Limitation identified:** A document ID or storage key must never be sufficient to bypass access control.

**Corrective action:** Check access on each protected download request, return a non-disclosing not-found response for unauthorized access, verify the stored content hash, and record denied attempts.

**Verification status:** The project owner reported that the unauthorized-viewer and authorized-viewer download tests passed. The denied-download test also verifies that a denial event is recorded. Tests later added for file-hash mismatch and permission revocation are documented in Entry 15.

**Evidence commit:** The denied-download test was reportedly committed as `test(security): verify unauthorized document downloads are denied`. Commit URL/hash pending; verify it from actual Git history. Verify the authorized-download test commit separately.

## Entry 8 — 2026-10-20 — Audit Logging and Administrative Review

**Prompt summary:** Record document activity and provide an administrator-only audit view.

**AI assistance summary:** Development covered audit events for downloads and denied downloads, uploads, deletion, document updates, and permission changes, as well as the paginated administrative audit endpoint.

**Limitation identified:** An audit table is not automatically tamper-resistant. Failures to persist audit events can also affect accountability.

**Corrective action:** Route relevant operations through the audit service and restrict audit-history access with server-side administrator authorization.

**Verification status:** Successful and denied download events, permission-change auditing, administrator access checks, and invalid pagination handling were reported tested. Resilience to audit-write failures and every failure path is bounded by the test coverage in the repository.

**Evidence commit:** Pending.

## Entry 9 — 2026-10-20 — Soft Deletion and Audit Preservation

**Prompt summary:** Support document deletion while retaining records needed by the audit history.

**AI assistance summary:** Development introduced a document deletion flag and a deletion endpoint that retains the database record and handles the stored file through the storage service.

**Limitation identified:** Permanently deleting a database row can break audit references and remove evidence needed for later review.

**Corrective action:** Use soft deletion and exclude deleted documents from normal listing and content access. Preserve audit rows rather than deleting them along with a document.

**Verification status:** The soft-delete implementation and schema change were developed. The project owner reports project testing complete; the final test suite should demonstrate deletion authorization, repeated-deletion behavior, storage cleanup, and audit preservation.

**Evidence commit:** Pending.

## Entry 10 — 2026-10-20 — Dashboard Integration

**Prompt summary:** Connect document management and permission operations to the TrustVault dashboard.

**AI assistance summary:** The dashboard was developed to support document upload, listing, search, download, sharing, revocation, metadata updates, and content replacement.

**Limitation identified:** Hiding controls in the interface is not an access-control boundary. The backend must independently enforce each protected operation.

**Corrective action:** Connect UI actions to API endpoints and retain server-side authorization as the security boundary.

**Verification status:** Manual UI testing of upload, listing/search, download, sharing, and revocation was reported. The document-preview action was added later and is recorded in Entry 21. Frontend behavior is distinct from backend authorization testing.

**Evidence commit:** Pending.

## Entry 11 — 2026-10-21 — Role-Specific Document Search

**Prompt summary:** Review search so users cannot retrieve metadata for documents outside their visibility scope.

**AI assistance summary:** Role-specific repository queries were recommended so search follows the same visibility rules as normal document listing.

**Limitation identified:** A generic accessible-document query may return owner-held documents to a viewer, or otherwise diverge from the intended role policy.

**Corrective action:** Keep viewers on a shared-documents-only query and owners on the owned-or-shared query. The final admin search behavior was aligned with the admin list: it uses `findOwnedOrSharedDocuments(user.getId())`, so explicitly shared documents can appear without listing every document. See Entries 16 and 21.

**Verification status:** The updated search code was saved, and the project owner reported the project fixes and tests complete. A final regression test should cover unshared, shared, owned, and deleted records for each applicable role.

**Evidence commit:** Pending.

## Entry 12 — 2026-10-21 — Audit Verification Failure and Development Re-baseline

**Prompt summary:** Troubleshoot `IllegalStateException: Audit event hash verification failed at event 19` raised by `AuditIntegrityService.verifyLocked()` when a download tried to append a new audit event.

**AI assistance summary:** The initial troubleshooting response hypothesized that database timestamp precision could cause a stored event hash to differ from its recalculated hash. It proposed refreshing newly persisted audit events before calculating hashes. When the failure persisted, a controlled re-baseline of the local development chain was proposed while retaining the audit rows.

**Mistake or limitation identified:** Timestamp precision was a hypothesis, not a confirmed root cause. The repeated failure showed that the timestamp correction alone did not resolve the mismatch. Clearing historical event hashes also removes their prior hash-link evidence; it cannot prove those records were unaltered before the new baseline.

**Corrective action:** On 2026-10-21, the local development chain was re-baselined. Existing `TV_AUDIT_LOGS` rows were retained, old `PREVIOUS_HASH` and `EVENT_HASH` values were cleared, and the chain head was reset for reinitialization with the configured `TRUSTVAULT_AUDIT_HMAC_KEY`. The recovery was intended for local development only, not as a production recovery procedure.

**Verification status:** Post-recovery chain initialization and later integrity verification were reported successful. This does not restore the lost historical hash-link evidence. Production deployment must start with an explicit, reviewed migration/baseline plan; do not repeat the local re-baseline casually against production data.

**Evidence commit:** Pending.

## Entry 13 — 2026-10-21 — Automated Audit Tampering Tests

**Prompt summary:** Add automated tests for modified audit records, deleted events, broken chain links, and altered checkpoints.

**AI assistance summary:** A Spring Boot integration-test class was proposed to exercise the integrity verifier against simulated tampering.

**Limitation identified:** A passing test is meaningful only if it reaches the intended verification branch and fails because an integrity invariant was violated, not because of unrelated database or configuration errors.

**Corrective action:** Configure the test environment, initialize the audit-chain head where required, and assert that the verifier rejects the tested tampering scenarios.

**Verification status:** The project owner reported that the tampering tests passed. The project notes describe four implemented scenarios; broader adversarial testing and deployment verification remain separate concerns.

**Evidence commit:** Pending.

## Entry 14 — 2026-10-22 — Expanding Authorization and Audit Test Coverage

**Prompt summary:** Expand tests for cross-user access, `VIEW` versus `EDIT`, immediate revocation, unauthorized uploads, document visibility, permission grants and revocations, and audit recording of denied actions.

**AI assistance summary:** Suggested Spring MockMvc controller tests, Mockito service tests, database-backed visibility tests, and integration tests for permission-change audit metadata and HMAC hashes.

**Mistake or limitation identified:** Some suggested test code contained unnecessary Mockito stubbings and mismatched repository methods. Mixing Spring-managed `@MockitoBean` dependencies with Mockito's `@InjectMocks` inside an integration-test class also introduced avoidable setup complexity.

**Corrective action:** Separate service unit tests from Spring integration tests, stub the repository method the implementation actually calls, initialize mocked document ownership correctly, and remove stubbings unused by the request path. Keep Mockito strictness enabled rather than masking issues with `lenient()`.

**Verification status:** Several authorization and audit test groups were reported passing. The project owner reports the remaining test sections verified. Add exact final test output/evidence when assembling the final commit history; do not infer a final test total from earlier, smaller test runs.

**Evidence commit:** Pending.

## Entry 15 — 2026-10-22 — SHA-256 Document Content Integrity

**Prompt summary:** Implement file integrity verification for upload, content replacement, and download, while preserving existing authorization and audit behavior.

**AI assistance summary:** Added `DocumentContentHashService` to calculate SHA-256 fingerprints, added `contentSha256` to `Document`, integrated hashing into upload and content replacement, and compared the stored file's hash before download.

**Specific issue found:** Existing download tests returned HTTP 500 because their mocked documents had no expected fingerprints. Some pre-existing database records also lacked fingerprints.

**Corrective action and verification:** Updated test fixtures with hashes matching their temporary files, added a regression test for modified stored content, added the Oracle `CONTENT_SHA256` column, generated a local SQL backfill based on current stored files, and updated `THREATS.md`. Reported passing tests included `viewerWithViewPermissionCanDownloadDocument`, `revokedPermissionImmediatelyPreventsNextDownload`, `downloadRejectsFileWhoseContentsHaveChanged`, `DocumentDownloadAuthorizationTests#userWithEditPermissionCanReplaceDocumentContent`, and `DocumentDownloadAuthorizationTests#uploadStoresSha256Fingerprint`. The complete `mvn test` run was reported as `BUILD SUCCESS` on 2026-10-22.

**Integrity limitation:** Backfilling hashes creates a baseline from the files' current contents; it does not prove historical integrity. SHA-256 comparison does not provide malware scanning or protect against an attacker able to change both the file and its trusted database fingerprint.

**Evidence commit:** Pending — record the actual commit hash or link after committing.

## Entry 16 — 2026-10-23 — Correcting Admin Visibility Without Listing the Entire Vault

**Prompt summary:** Review administrator document visibility after an explicitly shared document did not appear in the document workspace.

**AI assistance summary:** An earlier proposed change replaced the admin's role-scoped query with a query returning all non-deleted documents. That would have exposed metadata for every user's documents, regardless of whether the admin had been granted access.

**Mistake or limitation identified:** Listing every document merely because the requester is an administrator is broader than TrustVault's explicit-grant policy. Conversely, restricting admins to documents whose owner has role `ADMIN` excludes documents explicitly shared with the admin.

**Corrective action:** The final controller branches for both document listing and search were changed to use `findOwnedOrSharedDocuments(user.getId())` for an admin. This allows admin-owned documents and documents explicitly shared with that admin to appear without making every document in the vault visible. Separate preview/download endpoints check ownership or explicit `VIEW`/`EDIT` permission.

**Verification status:** The project owner reported all fixes complete. The controller code inspected during development also derived `isOwner` and `canEdit` using `isOwnerDoc || isAdmin`. If this remains the current behavior, it provides an administrator capability override for UI edit controls; confirm that the backend's metadata-update, replace, and delete rules match the intended policy before deployment. Listing visibility and content access are separate decisions.

**Evidence commit:** Pending — add the actual corrective commit link.

## Entry 17 — 2026-10-23 — Resolving Modular Spring Boot 4 Test Dependencies

**Prompt summary:** Fix test compilation errors related to Spring Boot test starters in `pom.xml`.

**AI assistance summary:** The initial suggestion incorrectly stated that modular starters such as `spring-boot-starter-webmvc-test` did not exist and removed them in favor of `spring-boot-starter-test`.

**Mistake or limitation identified:** Removing the web MVC test starter caused compilation errors, including missing `org.springframework.boot.webmvc.test.autoconfigure` and `AutoConfigureMockMvc` symbols. Spring Boot 4 uses modular test starters.

**Corrective action:** The local Maven repository was inspected and the available Spring Boot `4.1.1` modular artifacts were confirmed. The relevant web MVC, data JPA, security, and validation test starters were restored to `pom.xml`.

**Verification status:** `mvn test-compile` was reported successful with zero compilation errors. A later complete test run is documented in Entry 18.

**Evidence commit:** Described as `fix(build): restore Spring Boot 4 modular test starters in pom.xml`. Commit URL/hash pending.

## Entry 18 — 2026-10-23 — Administrative Audit-Chain Verification Endpoint

**Prompt summary:** Expose HMAC-SHA256 audit integrity verification through an administrator-only HTTP endpoint and test it.

**AI assistance summary:** Added `GET /api/admin/audit/integrity` in `AuditLogController.java`, restricted with `@PreAuthorize("hasRole('ADMIN')")`, plus the `UserManagementAndAuditTests#adminCanVerifyAuditIntegrity` integration test.

**Mistake or limitation identified:** The first test returned a failed status with `Audit chain head is missing` because the empty test database had no initial chain-head record before verification.

**Corrective action:** Test setup was updated to initialize the chain-head record (`ID=1`, `LAST_HASH='GENESIS'`, `CHECKPOINT_HMAC='UNINITIALIZED'`) and append an initial signed event. The endpoint returns structured status/message information.

**Verification status:** All nine tests in `UserManagementAndAuditTests` were reported passing. The complete test suite at that checkpoint reported 56 tests passed and zero failures. This is a historical test count, not necessarily the count of the final suite.

**Evidence commit:** Described as `feat(audit): expose audit chain integrity endpoint and test suite`. Commit URL/hash pending.

## Entry 19 — 2026-10-24 — Live Account and Role Validation

**Prompt summary:** Review session and authentication behavior so disabling a user or changing their role invalidates existing access.

**AI assistance summary:** Added `LiveUserAccountFilter` to check the database account, enabled status, and current role authorities on authenticated requests. Registered it in `SecurityConfig` and added regression tests.

**Specific issues found:** The first test run returned HTTP 401 instead of the expected authorization responses because some `@WithMockUser` identities did not match enabled database users. A subsequent fixture correction caused Mockito `UnfinishedStubbing` because a helper was called inside `thenReturn(...)`.

**Corrective action and verification:** Kept the production account-validation check enabled. Updated fixtures to use matching enabled database users or correctly configured mocks, and ran mock setup helpers before repository stubbing. Updated `THREATS.md` to document this control and its limitations. `LiveUserAccountFilterTests` reportedly passed all five filter-level tests; `LiveUserAccountRevocationIntegrationTests` reportedly passed both integration tests for disabled-account and role-change session invalidation. The full `mvnw.cmd clean test` suite was reported `BUILD SUCCESS`.

**Security behavior verified:** Filter tests covered disabled, missing, and role-changed accounts; API requests received HTTP 401 for invalid accounts; browser requests were redirected to login; and unauthenticated requests continued through the filter.

**Verification limitation:** The check happens on the next authenticated request and does not retroactively cancel a response already being processed. It also adds database work to authenticated requests. Production session behavior has not been independently verified.

**Evidence commit:** Pending — changes were reported as local and not yet committed/pushed at that checkpoint.

## Entry 20 — 2026-10-24 — Permission Audit Metadata and HMAC Protection

**Objective:** Improve the forensic record for successful and denied document-permission changes.

**Implementation:** Added recipient ID, recipient email snapshot, previous permission type, and new permission type to `AuditLog`. Extended the audit response projection and service methods to carry this metadata.

**Integrity protection:** Added versioned event-HMAC calculation for metadata-bearing events while retaining the original calculation format when historical metadata is absent.

**Failure auditing:** Updated denied permission grant/revoke paths to record available recipient and permission details.

**Tests:** Added tampering tests for recipient email and permission type, as well as audit API and authorization coverage. Mockito expectations were updated to match metadata-aware service calls. The complete `mvnw.cmd clean test` suite was reported `BUILD SUCCESS`.

**Database verification:** The four permission metadata columns were confirmed present in the Oracle development schema. `DOCUMENT_ID` had no null values, and `CK_TV_AUDIT_OUTCOME` was reported enabled and validated with `SUCCESS`, `REFUSED`, `FAILED`, and `DENIED` as allowed outcomes. The live development audit-integrity endpoint returned `OK`. The full migration script was not executed because the columns already existed; only the missing outcome constraint was added after prechecks.

**Deployment limitation:** The production database schema and production deployment have not been independently verified.

**Evidence commit:** Pending; changes were reported local and not committed/pushed at that checkpoint.

## Entry 21 — 2026-10-24 — In-Browser Document Preview and Storage Recovery

**Prompt summary:** Add a View action so a user with permission can read document content in the browser, rather than having only a Download option; then investigate document failures across the inventory instead of fixing only individual examples.

**AI assistance summary:** Added `DOCUMENT_VIEW` success and failure events through `AuditLogService`. Added `GET /api/documents/{id}/view` in `DocumentController.java`, retaining the existing download endpoint. Added a View button and handler in `static/js/dashboard.js` that requests the protected endpoint and opens the response in a separate browser tab.

**Security controls implemented:** The preview endpoint checks that the document is not deleted, verifies that the requester owns it or has explicit `VIEW`/`EDIT` permission, checks that the file exists and is readable, compares its SHA-256 fingerprint with the database value, and records a view event. It serves only an allowlist of inline MIME types (`application/pdf`, JPEG, PNG, GIF, WebP, and plain text) with `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`; unsupported formats return HTTP 415. HTML and SVG are not rendered inline by this endpoint.

**Issue identified during testing:** The UI displayed the same generic message for HTTP 404 responses caused by missing files and denied access, which made those causes difficult to distinguish from the frontend alone. The admin could also fail to see a document explicitly shared with them because the listing logic was restricted by the document owner's role.

**Corrective action:** The document inventory was checked against expected size and SHA-256 hashes. Four missing files were found in the active project storage directory. Verified copies were found in the backup/previous storage locations and restored only after both size and hash matched the database. The recovery script did not overwrite existing files. The four restored documents were IDs 1 (`ASS2`), 2 (`ASS10`), 3 (`ass`), and 17 (`XSS`). The remaining ten active document files passed size/hash checks in the active directory. No database permission rows were changed during recovery.

The admin list and search branches were aligned to `findOwnedOrSharedDocuments(user.getId())`, allowing admin-owned documents and documents explicitly shared with the admin to appear, rather than returning every document in the vault. The preview/download endpoints still check ownership or explicit permissions.

**Verification status:** The project owner reported successful build/test execution after the backend preview change and subsequently reported the storage and listing fixes complete. The inventory check confirmed all 14 non-deleted document files had matching size and SHA-256 values after restoration. The inventory check is evidence of file integrity at that time; it does not alone prove every browser preview and role/permission combination.

**Policy note:** Controller response fields seen during development derived `isOwner` and `canEdit` using `isOwnerDoc || isAdmin`. If still present, that exposes edit controls to an admin for any document in their list, including one shared with `VIEW` only. Confirm that backend metadata-update, replace, and delete authorization matches the intended role/permission policy before deployment. Listing visibility and content access are separate decisions.

**Evidence commit:** Pending — add the real commit link after reviewing history.

## Entry 22 — 2026-10-24 — Role Selection During Public Signup

**Prompt summary:** Add an account-role option to signup because the registration form previously offered no role selector.

**Issue identified:** `SignupController.SignupRequest` originally accepted only full name, email, and password, and signup hardcoded new users to `UserRole.VIEWER`.

**Corrective action:** Extended the request record with a role field; normalized the requested role using `Locale.ROOT`; accepted only `OWNER` or `VIEWER`; rejected other values, including `ADMIN`, with HTTP 400; and passed the validated `selectedRole` to the `AppUser` constructor. Added an Owner/Viewer dropdown to `signup.html` and updated the JSON request payload to send the selected role. Public signup does not allow self-assignment of administrator privileges; administrator provisioning remains separate.

**Verification status:** Source inspection confirmed `String role`, `requestedRole`, the `OWNER`/`VIEWER` allowlist, `selectedRole`, and use of `selectedRole` in the constructor. The project owner confirmed the role-selection work and reports the project tests complete. Add the final Maven output and end-to-end signup checks (Owner, Viewer, and direct API attempt with `ADMIN`) to the evidence record if not already captured elsewhere.

**Evidence commit:** Pending — add the actual commit link after reviewing history.

## Final Status at Handoff

- The project owner reports all project sections and tests verified and considers the implementation complete.
- The Oracle development/test schema and stored-file recovery work described above were performed without intentionally dropping `MYDATABASE` or deleting existing audit rows.
- The local development audit chain was re-baselined as documented in Entry 12. Older hash-link evidence from before that baseline is not recoverable from the current chain alone.
- Development verification is not the same as production deployment verification. Production credentials, environment variables, storage persistence and backup, schema migration, HMAC key management, TLS, session configuration, and operational monitoring must be checked against the actual deployment environment.
- **Commits and push:** No commit hash or GitHub URL is fabricated in this file. Add evidence links from the actual commit history after the relevant changes are committed and pushed.

## Logging Rules

- Keep entries tied to actual AI-assisted work. Do not invent prompts, AI outputs, corrections, commit hashes, links, or test results.
- Use the actual development date when known; distinguish retrospective documentation from the date a change was made.
- Add a relevant commit link only after verifying it in repository history.
- Distinguish proposed, implemented, manually tested, and automated-test-verified controls.
- Document known limitations, security-policy decisions, test gaps, and later changes that supersede earlier advice.
- When later implementation changes an earlier approach, update the older entry or point clearly to the later corrective entry so the log remains internally consistent.
