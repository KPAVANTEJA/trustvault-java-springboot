# TrustVault — Secure Document Sharing with Verifiable Audit Trails

TrustVault is a Java web application for secure document sharing. Its goal
is to enforce document-level permissions, support immediate access
revocation, and provide an auditable record of document activity.

## Technology Stack

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- Oracle Database
- Maven
- HTML, CSS and JavaScript

## Current Development Status

- [x] Initial Spring Boot project and static landing page
- [x] Oracle connection configuration through environment variables
- [ ] Verify session login and role-based access tests
- [ ] Document upload, download and validation
- [ ] Per-document read/edit permissions and immediate revocation
- [ ] Tamper-evident audit records
- [ ] Document search and administrator activity view
- [ ] Production deployment and demo recording

A checked setup item does not imply that unfinished document features
are already implemented. Update this checklist as features are tested.

## Local Setup

1. Install Java 17 and Maven, or use the Maven wrapper.
2. Configure an Oracle database that the application user can access.
3. Configure the required environment variables in your run configuration.
4. Run `TrustvaultApplication.java` from Eclipse as a Spring Boot app.

## Required Environment Variables

Database:
- `ORACLE_DB_URL`
- `ORACLE_DB_USERNAME`
- `ORACLE_DB_PASSWORD`

Initial development accounts:
- `TRUSTVAULT_ADMIN_EMAIL`
- `TRUSTVAULT_ADMIN_PASSWORD`
- `TRUSTVAULT_OWNER_EMAIL`
- `TRUSTVAULT_OWNER_PASSWORD`
- `TRUSTVAULT_VIEWER_EMAIL`
- `TRUSTVAULT_VIEWER_PASSWORD`

Use your own passwords of at least 12 characters. Never put real passwords
in this README, source code, or a committed configuration file.

## Sign-in

Open `/login` to use Spring Security's generated login page.
After sign-in, `/api/auth/me` returns the current authenticated identity
and authorities.

## Documentation

- `THREATS.md` — security threats and mitigations
- `PROMPTS.md` — dated AI-use and correction log
- `docs/API.md` — endpoint access requirements
- `docs/DATABASE.md` — database setup and schema notes

## Security Scope

Route-level role checks are only the first layer. Document operations must also check the authenticated user's permission for the specific document on every request. Audit and revocation claims will be updated only after the corresponding features and tests are implemented.