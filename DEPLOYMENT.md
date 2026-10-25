# Docurithm — Deployment & Operations Guide

This guide retains the direct-Oracle deployment procedures for local/Oracle-compatible environments. For the hosted Render + Supabase PostgreSQL demo, follow [`SUPABASE_DEPLOYMENT.md`](SUPABASE_DEPLOYMENT.md) instead; the `prod` profile in this branch is configured for Supabase PostgreSQL and private Supabase Storage.

---

## 1. System Requirements

- **Java Runtime:** OpenJDK 17 LTS (or higher)
- **Build Tool:** Apache Maven 3.8+ (or Maven Wrapper `./mvnw`)
- **Database:** Oracle Database 19c / 21c / 23ai (or XE)
- **Operating System:** Linux (Ubuntu 22.04+, RHEL 9+), macOS, or Windows 10/11 / Windows Server
- **Memory:** Minimum 2 GB RAM (4 GB recommended for production)
- **Disk:** Fast SSD with sufficient storage for the private document vault (`./trustvault-private-files/`)

---

## 2. Environment Variables Configuration

Docurithm reads all security-sensitive configuration from environment variables. Never commit secrets, passwords, or connection strings into version control.

### Required Environment Variables

| Variable | Description | Example Value |
|---|---|---|
| `ORACLE_DB_URL` | Oracle JDBC connection URL | `jdbc:oracle:thin:@//localhost:1521/YOUR_SERVICE_NAME` |
| `ORACLE_DB_USERNAME` | Oracle database user | `trustvault_app` |
| `ORACLE_DB_PASSWORD` | Oracle database user password | `<choose-a-unique-db-password>` |
| `TRUSTVAULT_AUDIT_HMAC_KEY` | Base64-encoded secret key (minimum 32 bytes) for HMAC-SHA256 audit chaining | `<generated-base64-secret>` |

### Optional Initial Account Seeding Variables

Configure these variables to automatically seed development accounts on first startup:

| Variable | Description | Example Value |
|---|---|---|
| `TRUSTVAULT_ADMIN_EMAIL` | Administrator account email | `admin@example.com` |
| `TRUSTVAULT_ADMIN_PASSWORD` | Administrator password (min 12 chars) | `<unique-password-at-least-12-characters>` |
| `TRUSTVAULT_OWNER_EMAIL` | Document owner account email | `owner@example.com` |
| `TRUSTVAULT_OWNER_PASSWORD` | Document owner password (min 12 chars) | `<unique-password-at-least-12-characters>` |
| `TRUSTVAULT_VIEWER_EMAIL` | Viewer account email | `viewer@example.com` |
| `TRUSTVAULT_VIEWER_PASSWORD` | Viewer password (min 12 chars) | `<unique-password-at-least-12-characters>` |
| `TRUSTVAULT_STORAGE_DIR` | Filesystem storage path for uploaded files | `/var/lib/trustvault/storage` |

---

## 3. Database Preparation (Oracle SQL)

Before starting the application, ensure the database user exists and has appropriate schema permissions:

```sql
-- Connect as SYSDBA
CREATE USER trustvault_app IDENTIFIED BY "<choose-a-unique-db-password>";
GRANT CONNECT, RESOURCE TO trustvault_app;
GRANT UNLIMITED TABLESPACE TO trustvault_app;
GRANT CREATE SEQUENCE TO trustvault_app;
GRANT CREATE TABLE TO trustvault_app;
```

Hibernate ddl-auto is set to `update` by default (`spring.jpa.hibernate.ddl-auto=update`), which will automatically initialize the tables and sequences on the first startup.

To manually execute the exact DDL statements, refer to [`docs/DATABASE.md`](docs/DATABASE.md).

---

## 4. Building the Application

Build the executable fat jar:

```bash
# Run tests and create package
mvn clean package

# Or build without running tests
mvn clean package -DskipTests
```

The resulting executable archive is generated at:
`target/trustvault-0.0.1-SNAPSHOT.jar`

---

## 5. Running the Application

### Option A: Standard Local Execution (with Oracle DB)

```bash
# On Linux / macOS:
export ORACLE_DB_URL="jdbc:oracle:thin:@//localhost:1521/YOUR_SERVICE_NAME"
export ORACLE_DB_USERNAME="trustvault_app"
export ORACLE_DB_PASSWORD="<choose-a-unique-db-password>"
export TRUSTVAULT_AUDIT_HMAC_KEY="<generated-base64-secret>"
export TRUSTVAULT_ADMIN_EMAIL="admin@example.com"
export TRUSTVAULT_ADMIN_PASSWORD="<unique-password-at-least-12-characters>"
export TRUSTVAULT_OWNER_EMAIL="owner@example.com"
export TRUSTVAULT_OWNER_PASSWORD="<unique-password-at-least-12-characters>"
export TRUSTVAULT_VIEWER_EMAIL="viewer@example.com"
export TRUSTVAULT_VIEWER_PASSWORD="<unique-password-at-least-12-characters>"

java -jar target/trustvault-0.0.1-SNAPSHOT.jar
```

```powershell
# On Windows (PowerShell):
$env:ORACLE_DB_URL = "jdbc:oracle:thin:@//localhost:1521/YOUR_SERVICE_NAME"
$env:ORACLE_DB_USERNAME = "trustvault_app"
$env:ORACLE_DB_PASSWORD = "<choose-a-unique-db-password>"
$env:TRUSTVAULT_AUDIT_HMAC_KEY = "<generated-base64-secret>"
$env:TRUSTVAULT_ADMIN_EMAIL = "admin@example.com"
$env:TRUSTVAULT_ADMIN_PASSWORD = "<unique-password-at-least-12-characters>"
$env:TRUSTVAULT_OWNER_EMAIL = "owner@example.com"
$env:TRUSTVAULT_OWNER_PASSWORD = "<unique-password-at-least-12-characters>"
$env:TRUSTVAULT_VIEWER_EMAIL = "viewer@example.com"
$env:TRUSTVAULT_VIEWER_PASSWORD = "<unique-password-at-least-12-characters>"

java -jar target/trustvault-0.0.1-SNAPSHOT.jar
```

The server will initialize on port `8081`.

---

### Option B: Zero-Dependency Local Evaluation (H2 In-Memory)

If an Oracle database instance is not available locally, you can run the application with the in-memory test profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

---

## 6. Production Deployment Architecture

For an internet-facing production deployment:

```
[ Client Browser ]
       │ HTTPS (Port 443)
       ▼
[ Nginx Reverse Proxy / Cloudflare ]
       │ HTTP (Reverse Proxy on 127.0.0.1:8081)
       │ - Terminates SSL/TLS
       │ - Sets X-Forwarded-For, X-Forwarded-Proto headers
       ▼
[ Docurithm Spring Boot Service ]
       │
       ├──► Isolated File Storage (/var/lib/trustvault/storage)
       │
       └──► Oracle Database Server (Port 1521)
```

### Production Nginx Reverse Proxy Configuration Example

```nginx
server {
    listen 80;
    server_name trustvault.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name trustvault.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/trustvault.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/trustvault.yourdomain.com/privkey.pem;

    client_max_body_size 12M;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Linux Systemd Service (`/etc/systemd/system/trustvault.service`)

```ini
[Unit]
Description=Docurithm Secure Document Vault
After=network.target

[Service]
User=trustvault
WorkingDirectory=/opt/trustvault
EnvironmentFile=/etc/trustvault/trustvault.env
ExecStart=/usr/bin/java -Xms512m -Xmx2048m -jar /opt/trustvault/trustvault-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

---

## 7. Operational Verification Checklist

After deploying the live instance, verify the following steps:

1. **Endpoint Health:** Open `https://your-domain/` and verify the landing page renders with dark glassmorphism styling.
2. **Authentication Flow:** Navigate to `/login`, click the "Demo Quick Fill" buttons, and sign in.
3. **Upload Test:** As `owner@example.com`, upload a PDF document. Verify it receives a category tag, size indicator, and SHA-256 fingerprint.
4. **Access Isolation:** Sign out, sign in as `viewer@example.com`. Confirm the uploaded document is **not visible**.
5. **Granting Access:** As owner, grant `VIEW` access to `viewer@example.com`.
6. **Download Test:** Sign in as viewer; verify the document is now visible and can be downloaded.
7. **Immediate Revocation Test:** As owner, click "Revoke" in the document's permission drawer. Sign in as viewer; verify download is immediately denied (HTTP 404).
8. **Admin Audit & Integrity Check:** Sign in as `admin@example.com`, navigate to `/admin.html`. Confirm that all uploads, downloads, grants, revocations, and denied attempts appear in the audit table. Click **"Verify Chain Integrity"** and ensure the green shield appears (`Audit Chain Verified: Intact & Tamper-Evident`).
