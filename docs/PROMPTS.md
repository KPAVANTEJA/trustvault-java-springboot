# AI Prompt and Correction Log

This file records AI assistance, identified mistakes or limitations, and corrective actions. Entries summarize actual project work and do not claim that unimplemented controls are functional.

## Entry 1 - 2026-10-19 - Application Startup

**Prompt summary:** Help start the Spring Boot TrustVault application.

**AI output:** Instructions focused on running the application before confirming the database configuration.

**Mistake identified:** The application had no configured datasource URL, so it failed at startup.

**Correction:** Configure Oracle connection settings using environment variables and verify that the application starts successfully.

**Evidence commit:** [36080b8](https://github.com/KPAVANTEJA/trustvault-java-springboot/commit/36080b87f6dc965f341837c98da8ce3aa408e9b2)

## Entry 2 - 2026-10-19 - Authorization

**Prompt summary:** Protect administrator, owner, and viewer operations.

**AI output:** Role-based server-side route restrictions were proposed.

**Limitation identified:** URL-level role checks alone cannot establish whether a user is authorized to access a specific document.

**Corrective action:** Implement service-layer, per-document authorization for every document operation and test that unauthorized cross-user access is denied. This work is pending verification.

**Evidence commit:** Not yet available.

## Entry 3 - 2026-10-19 - Audit Integrity

**Prompt summary:** Design an audit trail capable of detecting tampering.

**AI output:** A hash-linked audit sequence was proposed as a possible integrity control.

**Limitation identified:** A hash chain alone is insufficient if an attacker can rewrite all records and recompute their hashes.

**Corrective action:** Implement a protected integrity checkpoint and tests that detect modified, deleted, or reordered records. This work is pending.

**Evidence commit:** Not yet available.

