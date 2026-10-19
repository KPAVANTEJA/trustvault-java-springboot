# AI Prompt and Correction Log

This file records AI assistance, mistakes or limitations identified, and the corrective action Entries are summaries of actual project work, not claims that unimplemented controls already work.

## Entry 1 — 2026-10-19 — Application Startup

**Prompt summary:** Help start the Spring Boot TrustVault application.

**AI output:** Instructions focused on running the app before confirming
the database configuration.

**Mistake identified:** The application had no configured datasource URL,
so it failed at startup.

**Correction:** Configure Oracle connection settings using environment
variables, then verify that the application starts.

**Evidence commit:** https://github.com/KPAVANTEJA/trustvault-java-springboot/commit/36080b87f6dc965f341837c98da8ce3aa408e9b2.

## Entry 2 — 2026-10-19 — Authorization

**Prompt summary:** Protect administrator, owner, and viewer operations.

**AI output:** Role-based server-side route restrictions were proposed.

**Limitation identified:** URL-level role checks alone cannot prove that a
user has permission to access a particular document.

**Corrective action:** Add service-layer, per-document authorization for each
document operation and test cross-user denial. This is still pending.

**Evidence commit:** "".

## Entry 3 — 2026-10-19 — Audit Integrity

**Prompt summary:** Design an audit trail capable of detecting tampering.

**AI output:** A hash-linked audit sequence is a possible integrity control.

**Limitation identified:** A hash chain alone can be recomputed by an attacker
who can rewrite all records and hashes.

**Corrective action:** Add a protected integrity checkpoint and tests that
detect changed, removed, or reordered records. This is still pending.

**Evidence commit:** "".