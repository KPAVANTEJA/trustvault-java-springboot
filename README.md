# TrustVault — Secure Document Sharing with Verifiable Audit Trails

TrustVault is a Java web application designed to make confidential document sharing more secure. Its goal is to enforce document-level access permissions, support immediate access revocation, and maintain a verifiable audit trail of document activity.

The project focuses on a key security principle: **having an account or a role should not automatically grant access to every document.**

## Technology Stack

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Spring_Security-Authentication-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" alt="Spring Security"/>
  <img src="https://img.shields.io/badge/Spring_Data_JPA-ORM-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Data JPA"/>
  <img src="https://img.shields.io/badge/Oracle_Database-SQL-F80000?style=for-the-badge&logo=oracle&logoColor=white" alt="Oracle Database"/>
  <img src="https://img.shields.io/badge/Apache_Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven"/>
  <img src="https://img.shields.io/badge/HTML5-Markup-E34F26?style=for-the-badge&logo=html5&logoColor=white" alt="HTML5"/>
  <img src="https://img.shields.io/badge/CSS3-Styling-1572B6?style=for-the-badge&logo=css3&logoColor=white" alt="CSS3"/>
  <img src="https://img.shields.io/badge/JavaScript-Frontend-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black" alt="JavaScript"/>
</p>

**Development environment:** Eclipse IDE · Java 17 · Maven · Oracle Database

## Development Status

- [x] Initial Spring Boot application setup
- [x] Static landing page
- [x] Oracle datasource configuration through environment variables
- [x] Initial authentication and role-based access-control structure
- [ ] Verify login, session handling, and role boundaries with tests
- [ ] Complete and verify secure document upload and private storage
- [ ] Implement and test authorized document downloads
- [ ] Enforce per-document read/edit permissions
- [ ] Verify immediate access revocation
- [ ] Implement tamper-evident audit records and integrity verification
- [ ] Add document search and administrator activity views
- [ ] Complete security testing and production deployment
- [ ] Record the final demonstration

**Status note:** The application is under active development. Existing setup and authorization components must be verified through testing. Planned features are not represented as completed security controls.

## Planned Core Features

- **Authentication and roles** — administrator, document owner, and viewer.
- **Private document storage** — keep uploaded files outside public static directories.
- **Document-level authorization** — validate access to each requested document.
- **Access revocation** — deny future requests after a permission is revoked.
- **Verifiable audit trail** — record document activity and detect unauthorized changes to audit records.
- **Document discovery** — search and view only documents the authenticated user is authorized to access.
- **Administrative monitoring** — provide an activity view for security and operational review.

These features will be marked as implemented only after the relevant code and tests are verified.

## Local Setup

### Prerequisites

- Java 17
- Eclipse IDE with Spring support, or another Java IDE
- Maven or the project's Maven Wrapper
- Oracle Database with a valid service name and accessible database user

### Configure environment variables

Configure the following variables in your Eclipse run configuration.

**Oracle Database**

| Variable | Description |
|---|---|
| `ORACLE_DB_URL` | Oracle JDBC connection URL |
| `ORACLE_DB_USERNAME` | Database username |
| `ORACLE_DB_PASSWORD` | Database password |

Example URL:

`jdbc:oracle:thin:@//localhost:1521/YOUR_SERVICE_NAME`

Replace `YOUR_SERVICE_NAME` with the actual Oracle service name.

**Initial development accounts**

| Variable | Description |
|---|---|
| `TRUSTVAULT_ADMIN_EMAIL` | Administrator email |
| `TRUSTVAULT_ADMIN_PASSWORD` | Administrator password |
| `TRUSTVAULT_OWNER_EMAIL` | Document owner email |
| `TRUSTVAULT_OWNER_PASSWORD` | Document owner password |
| `TRUSTVAULT_VIEWER_EMAIL` | Viewer email |
| `TRUSTVAULT_VIEWER_PASSWORD` | Viewer password |

Use development-only credentials with strong passwords of at least 12 characters. Never commit real passwords, database credentials, or secrets to Git.

### Run the application

1. Import the project into Eclipse as an existing Maven project.
2. Configure the required environment variables.
3. Locate `TrustvaultApplication.java`.
4. Run it as a Spring Boot application.
5. Check the console for startup errors and confirm that the application starts successfully.

The application is configured to use port `8081` in the current development setup.

## Sign-in and Authentication

Open the local application and navigate to `/login` to access Spring Security's generated login page.

After successful authentication, the endpoint `/api/auth/me` can be used to inspect the current authenticated identity and authorities.

Authentication confirms the user's identity; it does not by itself grant access to every document.

## Database

The application uses Oracle Database for user accounts and document-related metadata.

Database connection settings are supplied through environment variables. Consult [`docs/DATABASE.md`](docs/DATABASE.md) for connection configuration, initial schema details, and database security notes.

## Project Documentation

- [`THREATS.md`](THREATS.md) — threat model, mitigations, and validation requirements
- [`PROMPTS.md`](PROMPTS.md) — AI assistance, identified limitations, and correction log
- [`docs/API.md`](docs/API.md) — API access requirements and endpoint documentation
- [`docs/DATABASE.md`](docs/DATABASE.md) — Oracle setup and database model

## Security Principles

- Enforce authorization on the server, not only in the frontend.
- Check document ownership and permissions for every protected document operation.
- Never trust a client-provided owner ID or storage path.
- Store password hashes instead of plaintext passwords.
- Keep uploaded files outside public web directories.
- Preserve CSRF protection for session-based requests.
- Record relevant security events and verify audit integrity.
- Test access denial, permission revocation, and cross-user isolation before claiming they work.

## Project Objective

TrustVault aims to demonstrate that secure document sharing requires more than basic CRUD operations. It must enforce who can access each document, respond correctly when permissions change, and provide trustworthy evidence of document activity.

The immediate priority is to complete and test the backend security controls before building the final user interface and preparing the demonstration.
