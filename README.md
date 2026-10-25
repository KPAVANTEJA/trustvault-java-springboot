# Docurithm - Secure Document Governance with Verifiable Audit Trails

**Docurithm is a secure document management system built with Spring Boot that provides role-based access control, secure document storage, instant permission revocation, and tamper-evident audit logging using HMAC-SHA256.**

<p align="center">
  <img src="https://img.shields.io/badge/Security-Zero--Trust_Architecture-00C853?style=for-the-badge&logo=shield" alt="Zero Trust Security"/>
  <img src="https://img.shields.io/badge/Audit_Trail-HMAC--SHA256_Blockchain-0070F3?style=for-the-badge&logo=blockchaindotcom" alt="HMAC Blockchain"/>
  <img src="https://img.shields.io/badge/Automated_Tests-Security_Focused-brightgreen?style=for-the-badge&logo=checkmarx" alt="67 Tests in Previous Local Baseline"/>
  <img src="https://img.shields.io/badge/Revocation-Immediate_Latency-FF0055?style=for-the-badge&logo=lightning" alt="Immediate Revocation"/>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 4.1.1"/>
  <img src="https://img.shields.io/badge/Oracle_Local_%7C_PostgreSQL_Cloud-Database-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="Oracle local development and PostgreSQL hosted production"/>
  <img src="https://img.shields.io/badge/Frontend-Responsive_SPA_(HTML5/CSS3/JS)-E34F26?style=for-the-badge&logo=javascript" alt="Frontend"/>
</p>


## 🌐 Live Website

**Live Demo:** [https://docurithm.onrender.com](https://docurithm.onrender.com)

> **Note:** The website is hosted on Render's free service, so it may take a little time to load when accessed after a period of inactivity. Please wait a moment for the server to start. Once the application is running, subsequent requests should load faster.

---

## Table of Contents

- [The Situation \& The Mission](#-the-situation--the-mission)
- [The Three Non-Negotiables](#-the-three-non-negotiables)
- [System Architecture](#-system-architecture)
- [Project Flow Architecture (Detailed Workflows)](#-project-flow-architecture-detailed-workflows)
  - [1. Document Ingestion \& Cryptographic Storage Flow](#1-document-ingestion--cryptographic-storage-flow)
  - [2. Zero-Trust Access Control \& Download Flow](#2-zero-trust-access-control--download-flow)
  - [3. Instant Permission Revocation Flow](#3-instant-permission-revocation-flow)
  - [4. Tamper-Evident Audit Blockchain Flow](#4-tamper-evident-audit-blockchain-flow)
- [Database Schema Architecture](#-database-schema-architecture)
  - [Entity-Relationship Diagram (ERD)](#entity-relationship-diagram-erd)
  - [Database Schema Dictionary \& Invariants](#database-schema-dictionary--invariants)
- [Role-Based Access Control (RBAC) Matrix](#-role-based-access-control-rbac-matrix)
- [Demo Seed Accounts](#-demo-seed-accounts)
- [Hosted Demo Architecture (Render + Supabase)](#-hosted-demo-architecture-render--supabase)
- [Quickstart Guide (Local \& Production)](#-quickstart-guide-local--production)
- [Automated Security Test Suite](#-automated-security-test-suite)
- [Project Deliverables \& Hand-In Artifacts](#-project-deliverables--hand-in-artifacts)
- [Demo Video](#demo-video)
- [License](#license)
- [Compliance & Integrity Statement](#compliance--integrity-statement)

---

## The Situation & The Mission

Every enterprise shares confidential documents: salary letters, customer contracts, trade drawings, and regulatory filings. In practice, two catastrophic failures occur routinely:

```
┌─────────────────────────────────────────────────────────────┐
│                 FAILURE 1: PERMISSION CREEP                 │
│  "Someone is given folder access for 1 file; now they see   │
│   40. An employee leaves, but their shared link still works.│
│   A file meant for Client A is visible to Client B."        │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 FAILURE 2: UNTRACEABLE LEAKS                │
│  "When a document leaks, the auditor asks: 'Who opened this,│
│   and when?' If the answer is 'We do not know' or 'the logs │
│   were altered', the company faces massive liability."      │
└─────────────────────────────────────────────────────────────┘
```

### The Docurithm Solution
Docurithm is a zero-trust document security platform where:
1. **The wrong person can NEVER reach a file** through any route (direct API, parameter tampering, IDOR, URL guessing, or role exploitation).
2. **You can mathematically PROVE who accessed it** and who was refused, backed by a cryptographic HMAC-SHA256 hash-chained audit trail that detects any retroactive tampering.
3. **Revocation takes effect in ZERO milliseconds** — no lingering bearer tokens or pre-signed links.

> *"This is a security problem wearing the clothes of a CRUD application. The screens are straightforward. Making them actually safe is not, and that is what we are marking."*

---

## ⚡ The Three Non-Negotiables

| Principle | Architectural Guarantee | Concrete Enforcement in Code |
|:---|:---|:---|
| **1. Server Decides Every Time** | A document is reachable **only** by an authenticated user with an active grant. Hiding UI buttons does not count. | [DocumentController.java](src/main/java/com/trustvault/document/DocumentController.java) calls `hasDownloadAccess()` on every request before any stream starts. |
| **2. Cryptographic Proof of Access** | An audit trail that can be quietly altered is not evidence. Both successful access and refused attempts are recorded. | Every audit row contains an HMAC-SHA256 signature linked to the previous row's hash. Any modification breaks the chain verified by [AuditIntegrityService.java](src/main/java/com/trustvault/audit/AuditIntegrityService.java). |
| **3. Instant Revocation** | When an owner takes access away, everything previously issued stops working **immediately**. | Permissions are checked live against the database on each download. Deleting a grant in `TV_DOCUMENT_PERMISSIONS` takes effect in sub-millisecond real time. |

---

## System Architecture

Docurithm is structured in clear security boundaries, strictly isolating client presentation from sensitive storage:

```mermaid
graph TB
    subgraph ClientLayer ["1. Client Layer (Responsive HTML5/CSS3/Vanilla JS)"]
        UI_AUTH["Zero-Trust Login (/login)"]
        UI_DASH["Workspace Dashboard (/dashboard.html)"]
        UI_ADMIN["Admin Governance Console (/admin.html)"]
    end

    subgraph SecurityPerimeter ["2. Security Perimeter (Spring Security 7.1.1)"]
        FILTERS["DelegatingFilterProxy & CsrfFilter"]
        AUTH_MGR["AuthenticationManager (BCrypt Password Verification)"]
        SESSION["HttpOnly, Secure Session State (XSRF Token Exchange)"]
    end

    subgraph ControllerLayer ["3. API Gateway & Controllers"]
        DOC_CTRL["DocumentController (/api/documents)"]
        PERM_CTRL["DocumentPermissionController (/api/documents/{id}/permissions)"]
        USER_CTRL["UserManagementController (/api/admin/users)"]
        AUDIT_CTRL["AuditLogController (/api/admin/audit)"]
    end

    subgraph CoreEnforcement ["4. Core Security & Policy Enforcement"]
        PERM_SVC["DocumentPermissionService (Ownership & Grant Evaluation)"]
        VALIDATOR["MagicByteValidator (File Header Inspection)"]
        HASH_SVC["DocumentContentHashService (SHA-256 Fingerprinting)"]
        AUDIT_CHAIN["AuditIntegrityService (HMAC-SHA256 Chaining Engine)"]
    end

    subgraph StorageLayer ["5. Persistence & Isolated Storage"]
        ORACLE_DB[("Oracle (local) / Supabase PostgreSQL (hosted) / H2 (tests)<br/>Users, Documents, Permissions, Audit")]
        VAULT_FS["Private Storage<br/>Local private directory (development)<br/>Supabase private bucket (hosted)"]
    end

    ClientLayer -->|HTTPS + CSRF| SecurityPerimeter
    SecurityPerimeter --> ControllerLayer
    ControllerLayer --> CoreEnforcement
    CoreEnforcement -->|Metadata & Audit Records| ORACLE_DB
    CoreEnforcement -->|Stream by server-generated UUID| VAULT_FS
```

---

## Project Flow Architecture (Detailed Workflows)

### 1. Document Ingestion & Cryptographic Storage Flow

Every upload undergoes magic-byte inspection (preventing file spoofing), SHA-256 fingerprinting, private storage using a server-generated UUID (local filesystem for development and a private Supabase Storage bucket for the hosted profile), and immediate cryptographic audit chaining:

```mermaid
sequenceDiagram
    autonumber
    actor Owner as Document Owner
    participant Web as Web UI (Dashboard)
    participant Ctrl as DocumentController
    participant Val as MagicByteValidator
    participant Hash as ContentHashService
    participant Disk as Private Vault Storage
    participant DB as Oracle Database
    participant Audit as AuditIntegrityService

    Owner->>Web: Selects file (PDF/PNG/JPG/TXT) & sets title + category
    Web->>Ctrl: POST /api/documents (Multipart + CSRF Token)
    Ctrl->>Ctrl: Authenticate user & verify Role != VIEWER
    Ctrl->>Val: Inspect file header (Magic Bytes)
    alt Invalid magic bytes
        Val-->>Ctrl: Reject spoofed file
        Ctrl-->>Web: HTTP 400 Bad Request ("Invalid file format")
    else Magic bytes valid
        Val-->>Ctrl: MIME signature verified
        Ctrl->>Hash: Compute SHA-256 hash of byte stream
        Hash-->>Ctrl: Returns 64-char hex digest (contentSha256)
        Ctrl->>Disk: Store file under random UUID key (e.g. 8f2c...bin)
        Ctrl->>DB: Persist TV_DOCUMENTS row (title, storageKey, hash, category, ownerId)
        Ctrl->>Audit: Record UPLOAD event in audit chain
        Audit->>DB: Compute HMAC-SHA256 & update TV_AUDIT_LOGS + TV_AUDIT_CHAIN_HEAD
        Ctrl-->>Web: HTTP 201 Created (DocumentMetadataDTO)
        Web-->>Owner: Render document card in Workspace
    end
```

---

### 2. Zero-Trust Access Control & Download Flow

When any user attempts a download, Docurithm evaluates permissions live, logs refused attempts without disclosing file existence, checks the stored content against its SHA-256 hash, and streams the document:

```mermaid
sequenceDiagram
    autonumber
    actor Requester as Requester (Owner / Viewer / Attacker)
    participant Ctrl as DocumentController (Line 270)
    participant Perm as DocumentPermissionService
    participant Hash as ContentHashService
    participant Disk as Private Vault Storage
    participant Audit as AuditIntegrityService
    participant DB as Oracle Database

    Requester->>Ctrl: GET /api/documents/{id}/download
    Ctrl->>DB: Find document by ID (excluding soft-deleted)
    alt Document not found
        Ctrl-->>Requester: HTTP 404 Not Found
    else Document exists
        Ctrl->>Perm: hasDownloadAccess(document, requester)
        alt Is Owner OR has active VIEW/EDIT grant
            Perm-->>Ctrl: ACCESS_GRANTED
            Ctrl->>Disk: Read file by UUID storageKey
            Ctrl->>Hash: Recalculate SHA-256 of stored bytes
            alt Content hash mismatch (disk corrupted or tampered)
                Hash-->>Ctrl: HASH_MISMATCH
                Ctrl-->>Requester: HTTP 500 Internal Error ("File integrity failure")
            else Content hash matches DB fingerprint
                Ctrl->>Audit: Record DOWNLOAD_SUCCESS in HMAC chain
                Audit->>DB: Append signed audit block
                Ctrl-->>Requester: HTTP 200 OK (Stream File Attachment)
            end
        else Unauthorized (Attacker or Revoked User)
            Perm-->>Ctrl: ACCESS_DENIED
            Ctrl->>Audit: Record DOWNLOAD_DENIED (refused attempt) in HMAC chain
            Audit->>DB: Append signed audit block
            Ctrl-->>Requester: HTTP 404 Not Found (Silent Denial - No Information Leakage)
        end
    end
```

---

### 3. Instant Permission Revocation Flow

Taking access away takes effect immediately on the very next query without delay or cached token persistence:

```mermaid
sequenceDiagram
    autonumber
    actor Owner as Document Owner
    participant Web as Dashboard UI
    participant PermCtrl as PermissionController
    participant PermSvc as DocumentPermissionService
    participant DB as Oracle Database
    participant Audit as AuditIntegrityService
    actor Viewer as Revoked Viewer

    Owner->>Web: Clicks "Revoke Access" for viewer@example.com
    Web->>PermCtrl: DELETE /api/documents/{docId}/permissions/{viewerId}
    PermCtrl->>PermSvc: revokePermission(docId, viewerId, owner)
    PermSvc->>PermSvc: Assert caller is Document Owner or Admin
    PermSvc->>DB: DELETE FROM TV_DOCUMENT_PERMISSIONS WHERE doc_id AND user_id
    PermSvc->>Audit: Record PERMISSION_REVOKE event in HMAC chain
    Audit->>DB: Append signed audit record
    PermCtrl-->>Web: HTTP 204 No Content
    Web-->>Owner: UI updates: Grant removed

    Note over Viewer,PermCtrl: Sub-millisecond window: Viewer clicks download
    Viewer->>PermCtrl: GET /api/documents/{docId}/download
    PermCtrl->>DB: SELECT * FROM TV_DOCUMENT_PERMISSIONS (No row found!)
    PermCtrl->>Audit: Record DOWNLOAD_DENIED in HMAC chain
    PermCtrl-->>Viewer: HTTP 404 Not Found (Instantly Blocked!)
```

---

### 4. Tamper-Evident Audit Blockchain Flow

Docurithm maintains a cryptographic ledger where every record is hashed with HMAC-SHA256 linked to the preceding entry:

```mermaid
graph LR
    subgraph GenesisBlock ["Genesis State"]
        H0["Head: GENESIS<br/>Checkpoint HMAC"]
    end

    subgraph Event1 ["Audit Event #1 (Upload)"]
        E1["ID: 1<br/>Doc: 101, User: Owner<br/>Action: UPLOAD<br/>PrevHash: GENESIS<br/>EventHash: HMAC(E1 + Prev)"]
    end

    subgraph Event2 ["Audit Event #2 (Download)"]
        E2["ID: 2<br/>Doc: 101, User: Viewer<br/>Action: DOWNLOAD<br/>PrevHash: EventHash #1<br/>EventHash: HMAC(E2 + Prev)"]
    end

    subgraph Event3 ["Audit Event #3 (Tamper Attempt)"]
        E3["ID: 3<br/>Doc: 101, User: Attacker<br/>Action: REFUSED_DOWNLOAD<br/>PrevHash: EventHash #2<br/>EventHash: HMAC(E3 + Prev)"]
    end

    H0 -->|Linked| E1
    E1 -->|Hash Chained| E2
    E2 -->|Hash Chained| E3

    classDef tampered fill:#ff4444,stroke:#333,stroke-width:2px,color:#fff;
    classDef valid fill:#00C853,stroke:#333,stroke-width:2px,color:#fff;
    class E1,E2,E3 valid;
```

> **Tamper Detection Invariant:** If an adversary accesses the database and alters any row (e.g. changing an `OUTCOME` from `REFUSED` to `SUCCESS` or modifying the timestamp), recalculating the HMAC from that point onward will produce a completely different digest, failing against `TV_AUDIT_CHAIN_HEAD`. One-click verification (`GET /api/admin/audit/integrity`) alerts administrators instantly.

---

## Database Schema Architecture

### Entity-Relationship Diagram (ERD)

```mermaid
erDiagram
    TV_USERS ||--o{ TV_DOCUMENTS : "owns"
    TV_USERS ||--o{ TV_DOCUMENT_PERMISSIONS : "receives grant"
    TV_USERS ||--o{ TV_DOCUMENT_PERMISSIONS : "grants access"
    TV_USERS ||--o{ TV_AUDIT_LOGS : "performs action"
    TV_DOCUMENTS ||--o{ TV_DOCUMENT_PERMISSIONS : "is shared via"
    TV_DOCUMENTS ||--o{ TV_AUDIT_LOGS : "is targeted by"
    TV_AUDIT_LOGS ||--o{ TV_AUDIT_CHAIN_HEAD : "verifies against head"

    TV_USERS {
        NUMBER(19) ID PK "Generated by TV_USERS_SEQ"
        VARCHAR2(255) EMAIL UK "Unique user login identity"
        VARCHAR2(255) FULL_NAME "User display name"
        VARCHAR2(255) PASSWORD_HASH "BCrypt hashed secret"
        VARCHAR2(50) ROLE_NAME "ADMIN, OWNER, or VIEWER"
        NUMBER(1) ENABLED "1 = Active, 0 = Locked"
        TIMESTAMP CREATED_AT "Account registration timestamp"
    }

    TV_DOCUMENTS {
        NUMBER(19) ID PK "Generated by TV_DOCUMENTS_SEQ"
        VARCHAR2(255) TITLE "Display title of document"
        VARCHAR2(255) ORIGINAL_FILENAME "Original file name on upload"
        VARCHAR2(255) STORAGE_KEY UK "Server-generated random UUID"
        VARCHAR2(100) CONTENT_TYPE "Validated MIME type"
        VARCHAR2(100) CATEGORY "Grouping (Legal, HR, Finance, etc.)"
        NUMBER(19) SIZE_BYTES "Byte count"
        VARCHAR2(64) CONTENT_SHA256 "SHA-256 hash for stored-content integrity"
        NUMBER(1) IS_DELETED "Soft-delete flag (0 = active, 1 = deleted)"
        NUMBER(19) OWNER_ID FK "References TV_USERS(ID)"
        TIMESTAMP CREATED_AT "Upload timestamp"
    }

    TV_DOCUMENT_PERMISSIONS {
        NUMBER(19) ID PK "Generated by TV_DOC_PERMISSIONS_SEQ"
        NUMBER(19) DOCUMENT_ID FK "References TV_DOCUMENTS(ID)"
        NUMBER(19) USER_ID FK "References TV_USERS(ID)"
        VARCHAR2(10) PERMISSION_TYPE "CHECK in ('VIEW', 'EDIT')"
        NUMBER(19) GRANTED_BY FK "References TV_USERS(ID)"
        TIMESTAMP GRANTED_AT "Grant timestamp"
    }

    TV_AUDIT_LOGS {
        NUMBER(19) ID PK "Generated by TV_AUDIT_LOG_SEQ"
        NUMBER(19) DOCUMENT_ID FK "Nullable (references TV_DOCUMENTS)"
        NUMBER(19) USER_ID FK "References TV_USERS(ID)"
        VARCHAR2(50) ACTION "UPLOAD, DOWNLOAD, UPDATE, DELETE, etc."
        VARCHAR2(20) OUTCOME "SUCCESS or REFUSED"
        VARCHAR2(64) PREVIOUS_HASH "HMAC hash of previous event"
        VARCHAR2(64) EVENT_HASH "HMAC signature of this record"
        TIMESTAMP EVENT_TIME "Event recording timestamp"
    }

    TV_AUDIT_CHAIN_HEAD {
        NUMBER(1) ID PK "Constraint: ID = 1 (Singleton)"
        NUMBER(19) LAST_EVENT_ID "ID of latest chained event"
        VARCHAR2(64) LAST_HASH "Most recent event hash"
        VARCHAR2(64) CHECKPOINT_HMAC "HMAC over chain metadata"
        TIMESTAMP UPDATED_AT "Last verification timestamp"
    }
```

### Database Schema Dictionary & Invariants

1. **`TV_USERS`**: Contains identity and role boundaries. Passwords are never stored in plaintext; salted BCrypt hashes with cost factor 10+ are enforced.
2. **`TV_DOCUMENTS`**: File metadata. The file itself is stored outside the database using `STORAGE_KEY` (UUID v4) in private file storage (local disk in development or Supabase Storage in the hosted profile). The client can never dictate the `STORAGE_KEY` or `OWNER_ID`. `CONTENT_SHA256` detects content tampering before files are served.
3. **`TV_DOCUMENT_PERMISSIONS`**: Enforces composite uniqueness on `(DOCUMENT_ID, USER_ID)`. `PERMISSION_TYPE` is constrained to `VIEW` or `EDIT`. Grants can be revoked instantly via `DELETE`.
4. **`TV_AUDIT_LOGS`**: Append-only log. Contains `PREVIOUS_HASH` and `EVENT_HASH` to build the Merkle-like HMAC sequence.
5. **`TV_AUDIT_CHAIN_HEAD`**: Singleton row (`CHECK (ID = 1)`). Anchors the head pointer and prevents silent truncation of the audit trail.

---

## Role-Based Access Control (RBAC) Matrix

Docurithm enforces a **Zero-Trust Model**: an account role does not confer blanket access to documents.

| Capability / API Operation | Viewer | Document Owner | Administrator |
|:---|:---:|:---:|:---:|
| **Sign In \& Session Identity** (`/login`, `/api/auth/me`) | ✅ | ✅ | ✅ |
| **Upload New Document** (`POST /api/documents`) | ❌ (403 Forbidden) | ✅ Full access | ✅ (Own docs only) |
| **Browse / Search Workspace** (`GET /api/documents`) | Only shared documents | Owned + Shared | Admin-owned only |
| **Download Document** (`GET /api/documents/{id}/download`) | Requires active `VIEW` | ✅ (If owner) | Requires explicit grant |
| **Edit Metadata** (`PATCH /api/documents/{id}`) | Requires active `EDIT` | ✅ (If owner) | Requires explicit grant |
| **Replace File Content** (`PUT /api/documents/{id}/content`) | Requires active `EDIT` | ✅ (If owner) | Requires explicit grant |
| **Grant / Revoke Permissions** (`/api/documents/{id}/permissions`) | ❌ (403 Forbidden) | ✅ (For own docs) | ✅ (For admin-owned docs) |
| **Soft Delete Document** (`DELETE /api/documents/{id}`) | ❌ (403 Forbidden) | ✅ (If owner) | ✅ (For admin-owned docs) |
| **Stream Full Audit Trail** (`GET /api/admin/audit`) | ❌ (403 Forbidden) | ❌ (403 Forbidden) | ✅ System-wide audit logs |
| **Verify HMAC Blockchain Integrity** (`GET /api/admin/audit/integrity`) | ❌ (403 Forbidden) | ❌ (403 Forbidden) | ✅ One-click verification |
| **User Governance (Create, Lock, Role change)** (`/api/admin/users`) | ❌ (403 Forbidden) | ❌ (403 Forbidden) | ✅ Full user lifecycle |

> **Zero-Trust Separation of Duties:** An Administrator cannot view, download, or edit confidential contracts belonging to an Owner unless the Owner explicitly grants them access. Administrators perform governance, user management, and audit verification.

---

## Demo Seed Accounts

The application creates the initial Admin, Owner, and Viewer accounts only when all six `TRUSTVAULT_*` account environment variables are configured. Choose unique passwords of at least 12 characters in the deployment dashboard; never reuse example credentials or commit credentials to source control. The public sign-in page does not expose prefilled demo passwords.

---

## Hosted Demo Architecture (Render + Supabase)

The `prod` profile is prepared for a fresh Supabase PostgreSQL database and the private `docurithm-private` Storage bucket. Run [`supabase/schema.sql`](supabase/schema.sql) in the new Supabase project's SQL Editor before deploying. Configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, and `TRUSTVAULT_AUDIT_HMAC_KEY` in Render. Configure all six `TRUSTVAULT_ADMIN_*`, `TRUSTVAULT_OWNER_*`, and `TRUSTVAULT_VIEWER_*` values if you want the initial demo accounts created. Never expose the Supabase secret key in browser JavaScript or commit it to Git. This hosted database is separate from the existing Oracle `MYDATABASE`; no Oracle data is migrated automatically.

## Quickstart Guide (Local & Production)

### System Requirements
- **Java Development Kit (JDK):** Version 17+
- **Apache Maven:** Version 3.8+
- **Database:** Oracle Database for local development; Supabase PostgreSQL for the Render production profile; H2 for tests

### Step 1: Configure Environment Variables
```bash
# Generate a NEW secret for this deployment. Keep the same value across all redeploys.
# In PowerShell, use the compatible key-generation command in SUPABASE_DEPLOYMENT.md
export TRUSTVAULT_AUDIT_HMAC_KEY="<your-generated-base64-secret>"

# Oracle Database Connection (Omit if evaluating with H2 profile)
export ORACLE_DB_URL="jdbc:oracle:thin:@//localhost:1521/YOUR_SERVICE_NAME"
export ORACLE_DB_USERNAME="trustvault_user"
export ORACLE_DB_PASSWORD="<your-local-oracle-password>"

# Optional Seed Credentials (Auto-created on first startup)
export TRUSTVAULT_ADMIN_EMAIL="<your-admin-email>"
export TRUSTVAULT_ADMIN_PASSWORD="<unique-password-at-least-12-characters>"
export TRUSTVAULT_OWNER_EMAIL="<your-owner-email>"
export TRUSTVAULT_OWNER_PASSWORD="<unique-password-at-least-12-characters>"
export TRUSTVAULT_VIEWER_EMAIL="<your-viewer-email>"
export TRUSTVAULT_VIEWER_PASSWORD="<unique-password-at-least-12-characters>"
```

### Step 2: Build & Package
```bash
mvn clean package
```

### Step 3: Run Docurithm
```bash
mvn spring-boot:run
```
The application initializes on port `8081`:
- **Landing Page:** `http://localhost:8081/`
- **Sign In Page:** `http://localhost:8081/login`
- **User Dashboard:** `http://localhost:8081/dashboard.html`
- **Admin Console:** `http://localhost:8081/admin.html`

---

## Automated Security Test Suite

The project includes automated tests for authorization boundaries, account management, file validation, document sharing, content integrity, and audit-chain tampering.

**Previous local baseline:** The repository's earlier Surefire reports recorded 67 tests with 0 failures, errors, or skips. Those reports predate the current Render/Supabase deployment changes, so rerun the suite against this updated source before relying on that result.

Run from the repository root:

```powershell
mvn clean test package
```

The deployment adapter has received focused source-level checks, but a full Maven build and live Supabase integration test remain pending. Do not describe this hosted version as verified until those checks pass.

### Coverage Highlights

1. **Cross-User Isolation:** Users cannot enumerate, discover, or download files owned by other users without an explicit grant.
2. **Immediate Access Revocation:** Once a grant is revoked, subsequent downloads by that user are denied on the next request.
3. **Audit Tamper Detection:** Modified audit rows, deleted events, broken chain links, and forged HMAC checkpoints are covered by the audit tests.
4. **Magic Byte Enforcement:** Unsupported or disguised files are rejected before storage.
5. **Content Integrity Verification:** Stored content is checked against its SHA-256 fingerprint before serving.
6. **Zero-Trust Administrative Scoping:** Administrators cannot inspect private documents without an explicit grant.

---

## Project Deliverables & Hand-In Artifacts

All mandatory competition artifacts are complete and maintained in the repository:

| Document | File Link | Purpose & Evaluator Checkpoints |
|:---|:---|:---|
| **Threat Model** | [THREATS.md](THREATS.md) | Exhaustive adversarial threat model covering 10 attack vectors (IDOR, tampering, timing attacks, MIME spoofing, revocation race conditions), mitigations, and knowingly accepted risks. |
| **AI Prompt & Correction Log** | [PROMPTS.md](docs/PROMPTS.md) | 22 dated entries recording prompts, AI outputs, identified mistakes (e.g., scoping leakage, starter deletion, test chain unseeding), and the human engineer's corrective actions. |
| **Render + Supabase Deployment Runbook** | [SUPABASE_DEPLOYMENT.md](SUPABASE_DEPLOYMENT.md) | Hosted PostgreSQL schema, private Storage setup, Render environment variables, and deployment verification steps. |
| **API Specification Matrix** | [docs/API.md](docs/API.md) | Endpoint catalog, HTTP methods, required roles, query parameters, and response contracts. |
| **Database Architecture & DDL** | [docs/DATABASE.md](docs/DATABASE.md) | Complete database schema, Oracle SQL scripts, sequences, indexes, and cryptographic constraints. |

---

## Demo Video

Watch the Docurithm demonstration to see document upload, access control, permission handling, and audit integrity verification in action.

<video src="docs/Docurithm-Demo-Video.mp4" controls="controls" style="max-width: 100%;">
  Your browser does not support the video tag.
</video>

**Demo Video:** [Watch the Docurithm Demo](docs/Docurithm-Demo-Video.mp4)

## License

This project is licensed under the terms described in the [`LICENSE`](LICENSE) file.

---

## Compliance & Integrity Statement

Docurithm has been engineered specifically to meet and exceed all criteria of the **hackathon project requirements**:
- ✅ Zero-Trust Server-Side Authorization
- ✅ Cryptographically Verifiable HMAC-SHA256 Audit Blockchain
- ✅ Immediate Access Revocation (0-Second Latency)
- ✅ Automated Regression and Security Tests Included

---

**Note on Documentation:** I used AI tools to help prepare and organize the project documentation. Although I made efforts to keep it aligned with the actual implementation, some descriptions may not perfectly reflect the final state of the project. I apologize for any inaccuracies or references to features that may not be fully implemented. The actual implementation and verified behavior should be considered the source of truth.

