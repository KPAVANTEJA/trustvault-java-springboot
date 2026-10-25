# Docurithm — Project Submission & Architecture Write-Up

## 1. Executive Summary: What We Built

Docurithm was engineered to solve the real-world crisis of corporate document leakage: **excessive permissions** and **unprovable access logs**. We built an enterprise-grade secure document platform that treats security as an uncompromising server-side architectural boundary rather than a UI decoration.

Key completed capabilities:
1. **Three-Tier Role Architecture:** `ADMIN` (system governance and audit monitoring), `OWNER` (uploading, categorizing, and managing document permissions), and `VIEWER` (read-only consumer of explicitly shared files).
2. **Per-Document Fine-Grained Permissions:** Individual grants for named user emails (`VIEW` or `EDIT`), strictly decoupled from broad administrative or group rights.
3. **Instant Access Revocation:** Every protected request verifies the live permissions table in an atomic transaction; revoking a grant denies access on the very next HTTP request.
4. **Isolated Private File Storage:** Local development stores files outside the web root; the hosted profile uses a private Supabase Storage bucket. Server-generated UUID keys and server-side authorization protect file access.
5. **Magic-Byte Content Validation:** Verifies raw byte headers (`%PDF`, `\x89PNG`, `\xFF\xD8\xFF`, ASCII/UTF-8 text) to reject disguised executable payloads.
6. **SHA-256 Content Verification:** Fingerprints files upon upload and replacement, re-verifying stored content before streaming to detect tampering.
7. **Cryptographic Blockchain Audit Trail:** Chained event log signed with an HMAC-SHA256 secret key (`TRUSTVAULT_AUDIT_HMAC_KEY`) anchored to a checkpoint head. Records successful and denied access attempts.
8. **Administrative Governance UI:** Real-time paginated audit stream, one-click cryptographic chain verification, and full user directory governance (account creation, role reassignment, and active status toggling).
9. **Automated Security Tests:** Test classes cover cross-user isolation, denied download auditing, revocation enforcement, and tampering detection. Rerun the suite after the hosted-storage changes before claiming a current passing result.

---

## 2. What We Deliberately Chose NOT to Build (and Why)

A smaller system with a permission model that holds is fundamentally better than a larger system with a security hole. We made explicit architectural decisions to omit specific features:

1. **Deliberately Omitted: Folder Inheritance & Group Hierarchy**
   - *Why:* Folder inheritance is the single greatest cause of corporate document leakage ("someone is given access to a folder so they can see one file, and they can now see forty"). In Docurithm, permissions are strictly per-document grants to named people.
2. **Deliberately Omitted: Public / Anonymous Shareable Links**
   - *Why:* Public link URLs (e.g. `trustvault.com/share/token123`) can be forwarded, indexed, leaked in browser histories, or retained after employees leave a company. Docurithm requires authenticated identity for all document operations so every access is legally attributable.
3. **Deliberately Omitted: Automatic AI-Driven Permission Expansion**
   - *Why:* AI models can propose helpful suggestions, but giving an automated model the authority to widen permissions on its own creates critical access drift. In Docurithm, only human document owners can confirm and execute a permission grant, and the grant is immediately logged in the audit trail.
4. **Deliberately Omitted: Permanent Database Row Deletion (Hard Deletes)**
   - *Why:* Hard-deleting database records destroys foreign-key references and breaks forensic evidence. Docurithm implements soft-deletion (`IS_DELETED = 1`), purging the physical stored file while preserving the document metadata and complete immutable audit history.

---

## 3. The Permission Model in Our Own Words

In Docurithm, authorization is governed by the principle: **"A role is not a key to every lock."**

- **Ownership Privilege:** When a user uploads a document, they become its immutable owner. Only the owner (or an administrator performing security governance) can grant or revoke permissions.
- **Grants to Named Individuals:** Access is granted to specific, registered email addresses.
  - `VIEW`: Permits file downloading and viewing metadata. Prohibits modifying file content, editing titles/categories, deleting the document, or sharing with others.
  - `EDIT`: Permits downloading, updating document metadata (title and category), and uploading replacement file contents (which triggers recalculation of the SHA-256 fingerprint). Does *not* allow deleting the document or altering permissions.
- **Strict Role-Scoping:**
  - Even an `ADMIN` cannot read an owner's private files unless explicitly granted access. The administrator's role is to verify the audit chain and govern user accounts.
  - When an unauthorized user attempts to download a file or access an ungranted document, the server responds with **HTTP 404 (Not Found)** rather than HTTP 403. This prevents malicious actors from determining whether a specific confidential document ID exists on the system.
- **Immediate Revocation:** We chose transactional database evaluation over stateless JWT permission claims. While JWT claims would avoid database queries, they cannot be revoked without waiting for token expiration. In Docurithm, immediate security supersedes micro-optimizations: deleting a permission row instantly revokes access for the very next request.

---

## 4. How We Used AI — and Where It Was Wrong

Throughout development, AI was used extensively for rapid scaffolding, test writing, and refactoring. However, AI repeatedly proposed insecure or flawed solutions that required strict human correction. Three critical examples from `PROMPTS.md`:

1. **AI Broadened Admin Visibility (Authorization Flaw):**
   - *What the AI did:* When asked to connect document search and listing for administrators, the AI replaced the owner-scoped query with `findAllByDeletedFalse()`, granting administrators global read access to all users' confidential documents.
   - *Why it was wrong:* This violated the core security premise that administrators must not have automatic peeking rights into employees' private files.
   - *Our correction:* We caught the violation through our automated test `adminSeesAdminOwnedDocumentsOnly`, rejected the AI's proposal, and preserved strict role-specific repository scoping.
2. **AI Erroneously Deleted Modular Test Dependencies (Build Flaw):**
   - *What the AI did:* When troubleshooting Spring Boot test starters, the AI hallucinated that `spring-boot-starter-webmvc-test` did not exist and deleted it from `pom.xml`.
   - *Why it was wrong:* Spring Boot 4 modularized test starters; removing it broke `@AutoConfigureMockMvc` imports across 4 test suites.
   - *Our correction:* We inspected the local `.m2` repository, confirmed that `spring-boot-starter-webmvc-test:4.1.1` existed, and restored the full modular starter configuration.
3. **AI Omitted Audit Chain Head Initialization in Test Fixtures (Testing Flaw):**
   - *What the AI did:* The AI created new admin integration tests calling `GET /api/admin/audit/integrity` without seeding the initial chain head row in the in-memory test database.
   - *Why it was wrong:* The integrity verifier expects the chain head to be initialized before verifying blocks; the test failed with `Audit chain head is missing`.
   - *Our correction:* We added proper `@BeforeEach` setup to seed the genesis chain head and append an initial signed block, verifying that tests fail only when real tampering occurs.

---

## 5. Known Limitations & What Is Unfinished

In the spirit of honest engineering:

1. **Key Management in Production:**
   - The HMAC secret (`TRUSTVAULT_AUDIT_HMAC_KEY`) is stored as an environment variable. If an attacker gains full root access to the server operating system and reads memory, they could forge subsequent audit blocks. Production environments should use a Hardware Security Module (HSM) or cloud KMS.
2. **Deep Anti-Virus Sandboxing:**
   - Magic-byte validation successfully blocks disguised binaries and executables. However, an attacker could upload an authentic PDF containing an embedded zero-day exploit or malicious PDF script. Integrating an external sandboxed virus scanning service (such as ClamAV) is recommended for high-risk enterprise deployments.
3. **Database Write-Once (WORM) Storage:**
   - The HMAC chain cryptographically detects database tampering after the fact. However, it cannot prevent a rogue database administrator with `DROP TABLE` permissions from deleting the database entirely. A production architecture should stream audit events to an append-only WORM cloud bucket (e.g. AWS S3 Glacier with Object Lock).
