# AI Prompt and Correction Log

This file records AI assistance, identified mistakes or limitations, corrective actions, and verification evidence during TrustVault development. Prompt summaries are based on actual project work and are not necessarily verbatim prompts. Proposed controls are distinguished from implemented and tested controls.

## Entry 1 - 2026-10-19 - Application Startup

**Prompt summary:** Help start the Spring Boot TrustVault application.

**AI output:** Instructions focused on running the application before confirming the database configuration.

**Mistake identified:** The application had no configured datasource URL, so it failed at startup.

**Correction:** Configure Oracle connection settings using environment variables and verify successful application startup.

**Verification status:** Record the actual startup result and configuration checks before describing the setup as fully verified.

**Evidence commit:** Not yet available.

## Entry 2 - 2026-10-19 - Role-Based Authorization

**Prompt summary:** Protect administrator, owner, and viewer operations.

**AI output:** Server-side Spring Security restrictions and method-level authorization were proposed and developed.

**Limitation identified:** Role checks on URLs alone cannot establish whether a user is authorized to access a particular document.

**Corrective action:** Add server-side authorization checks for protected document operations and automated tests for role boundaries.

**Verification status:** Five existing role-authorization tests passed for their tested endpoint boundaries. Document-level authorization requires additional tests beyond these role checks.

**Evidence commit:** Not yet available.

## Entry 3 - 2026-10-19 - Audit Integrity

**Prompt summary:** Design an audit trail capable of detecting tampering.

**AI output:** A hash-linked audit sequence was proposed as a possible integrity control.

**Limitation identified:** A hash chain alone is insufficient if an attacker can rewrite all records and recompute their hashes.

**Corrective action:** Implement protected integrity checkpoints and verification tests that detect modified, deleted, or reordered records.

**Verification status:** Tamper-evident audit integrity remains pending until implementation and testing are complete.

**Evidence commit:** Not yet available.

## Entry 4 - 2026-10-19 - Document Upload and Private Storage

**Prompt summary:** Implement document uploads, metadata persistence, and file storage.

**AI assistance summary:** Development work covered the upload endpoint, document metadata, ownership association, storage identifiers, and file-storage handling.

**Security consideration identified:** Client-supplied filenames and metadata must not allow users to select another owner or control the server's storage path.

**Corrective action:** Use the authenticated user to establish ownership and server-generated UUID storage keys. Upload validation and the reported 10 MB size limit require verification against the actual configuration.

**Verification status:** Upload functionality has been implemented and manually exercised. Additional automated validation tests remain necessary.

**Evidence commit:** Not yet available.

## Entry 5 - 2026-10-19 - Document Listing and Access Boundaries

**Prompt summary:** Restrict document listings to documents visible to the authenticated user.

**AI assistance summary:** Role-specific repository queries and controller logic were developed for ADMIN, OWNER, and VIEWER users.

**Limitation identified:** A generic listing query can expose metadata for documents that the authenticated user should not see.

**Corrective action:** Apply role-specific visibility rules: ADMIN sees ADMIN-owned documents, OWNER sees owned or shared documents, and VIEWER sees explicitly shared documents only.

**Verification status:** The updated listing code was saved and the user reported that the existing seven tests passed. Dedicated cross-user listing tests remain necessary.

**Evidence commit:** Not yet available.

## Entry 6 - 2026-10-20 - Per-Document Sharing and Revocation

**Prompt summary:** Allow document owners to grant and revoke VIEW or EDIT access for named users.

**AI assistance summary:** Development work covered the permission service, permission endpoints, recipient validation, ownership checks, and sharing controls.

**Limitation identified:** Authentication alone does not authorize a user to change sharing permissions for another user's document.

**Corrective action:** Enforce ownership checks when granting, listing, or revoking document permissions. Validate recipient and permission information on the server.

**Verification status:** Manual end-to-end testing confirmed that a VIEW permission allowed a recipient to download a document and that revoking the permission prevented the recipient's next download. Permission endpoints and audit events were also exercised manually.

**Evidence commit:** Not yet available.

## Entry 7 - 2026-10-20 - Authorized and Unauthorized Document Downloads

**Prompt summary:** Implement protected document downloads and test access-denial behavior.

**AI assistance summary:** The download endpoint was developed to verify ownership or explicit document permissions before returning a file. Automated tests were added for unauthorized and authorized downloads.

**Limitation identified:** A document identifier or storage key must never be sufficient to bypass authorization.

**Corrective action:** Check current permissions on each protected download request, return a non-disclosing not-found response for unauthorized access, and record denied download attempts.

**Verification status:** The user confirmed that both download authorization tests passed: an unauthorized viewer was denied access, while a viewer with VIEW permission could download the document. The unauthorized test also verifies that a denial event is recorded.

**Evidence commit:** The denied-download test was committed under `test(security): verify unauthorized document downloads are denied`. Not yet available. Verify the commit for the authorized-download test separately.

## Entry 8 - 2026-10-20 - Audit Logging and Administrative Review

**Prompt summary:** Record document activity and provide an administrator-only audit view.

**AI assistance summary:** Development work covered audit events, recording successful and denied downloads, deletion and permission-change events, and the paginated administrative audit endpoint.

**Limitation identified:** An audit table does not automatically provide tamper resistance, and failed audit writes may affect accountability.

**Corrective action:** Record relevant document events and restrict administrative audit access using server-side authorization.

**Verification status:** The user confirmed successful and denied download audit events, permission-change auditing, and administrator access checks. Invalid pagination parameters were also reported to return HTTP 400. Tamper resistance and comprehensive audit-failure handling remain unverified.

**Evidence commit:** Not yet available.

## Entry 9 - 2026-10-20 - Soft Deletion and Audit Preservation

**Prompt summary:** Support document deletion while preserving records required for audit history.

**AI assistance summary:** Development work introduced a deletion flag and a deletion endpoint that updates the document record and handles stored-file removal.

**Limitation identified:** Permanently deleting a database row can break audit references and remove evidence needed for later review.

**Corrective action:** Retain the document database record with a deleted flag and remove the associated stored file through the storage service.

**Verification status:** The soft-delete implementation and schema change were developed. Automated tests for deletion authorization, repeated deletion, storage cleanup, and audit preservation remain recommended.

**Evidence commit:** Not yet available.

## Entry 10 - 2026-10-20 - Dashboard Integration

**Prompt summary:** Connect document management and permission operations to the TrustVault dashboard.

**AI assistance summary:** The frontend was developed to support document upload, listing, search, download, sharing, and revocation.

**Limitation identified:** Hiding controls in the interface does not prevent unauthorized API requests. The backend must enforce all access restrictions independently.

**Corrective action:** Connect dashboard actions to protected API endpoints and retain server-side authorization as the security boundary.

**Verification status:** The user confirmed manual testing of upload, document search, download, sharing, and revocation UI behavior. Dedicated frontend regression tests and broader negative authorization tests remain necessary.

**Evidence commit:** Not yet available.

## Entry 11 - 2026-10-21 - Role-Specific Document Search

**Prompt summary:** Review document search so every authenticated role can search without exposing unauthorized documents.

**AI output:** Role-specific repository queries were recommended to apply the same access rules to search results as to normal document listings.

**Limitation identified:** The VIEWER branch used `searchAccessibleDocuments()`, which includes owned documents as well as shared documents and is broader than the intended VIEWER policy.

**Corrective action:** Use a shared-documents-only search query for VIEWER users, an owned-or-shared query for OWNER users, and an ADMIN-specific query for ADMIN users.

**Verification status:** The correction must be confirmed in the saved code and tested before it is described as verified.

**Evidence commit:** Not yet available.

## Entry 12 - 2026-10-21 - Audit Integrity Verification Failure and Development Re-baseline

**Prompt summary:** Troubleshoot the `IllegalStateException: Audit event hash verification failed at event 19` exception raised by `AuditIntegrityService.verifyLocked()` when a download attempted to append a new audit event.

**AI output:** The initial troubleshooting response suggested that database timestamp precision might cause a stored event hash to differ from the recalculated hash. It proposed refreshing newly persisted audit events before calculating their hashes. After the same verification failure persisted, a controlled re-baseline of the local development chain was proposed, retaining audit rows while clearing their old hash values and resetting the chain-head row.

**Mistake or limitation identified:** The timestamp-precision explanation was a hypothesis, not a confirmed root cause. The repeated failure showed that the proposed timestamp correction alone did not resolve the existing mismatch. Clearing old event hashes also removes their previous individual hash-link evidence; it cannot prove that the historical records were unaltered before the new baseline was created.

**Corrective action:** On 2026-10-21, the local development audit chain was re-baselined. Existing rows in `TV_AUDIT_LOGS` were retained, the old `PREVIOUS_HASH` and `EVENT_HASH` values were cleared, and the chain head was reset for reinitialization using the configured `TRUSTVAULT_AUDIT_HMAC_KEY`. The purpose was to recover the development chain without deleting the audit records. This recovery was not intended for production use.

**Verification status:** The re-baseline is a recovery step, not proof that tamper detection is complete. Confirm successful post-recovery initialization, verify new chained events, and add automated tests for modified, deleted, reordered and unchained records, as well as invalid checkpoints. Until these checks pass, audit integrity remains implemented but not fully verified.

**Evidence commit:** Not yet available. No commit link is recorded here because one has not been supplied.

## Logging Rules

- Keep entries tied to actual AI-assisted work. Do not invent prompts, AI outputs, corrections, or test results.
- Verify the date of each entry using the original interaction or reliable project history.
- Link relevant commits where available; never invent commit hashes or GitHub links.
- Distinguish proposed, implemented, manually tested, and automated-test-verified controls.
- Record known limitations and update earlier entries when later work changes the implementation.
