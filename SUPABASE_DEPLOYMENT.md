# Docurithm — Render + Supabase Deployment Guide

This guide deploys the hosted demo using **Render (Spring Boot web service)**, **Supabase PostgreSQL (metadata, users, permissions, audit history)**, and a **private Supabase Storage bucket (document bytes)**.

The existing local Oracle `DATABASE` remains separate and untouched. The hosted demo starts with a new empty Supabase database; no Oracle data or uploaded local documents are copied automatically.

## 1. Finish Supabase setup

You should already have a Supabase project and a **private** bucket named `docurithm-private`.

1. Open the Supabase project and select **SQL Editor**.
2. Open `supabase/schema.sql` in this repository, copy its full contents into a new SQL query, and run it once in the new Supabase project's database.
3. Confirm the query succeeds. It creates the application tables and sequences, plus the initial audit-chain-head control row. It does not import existing Oracle records.
4. Open **Connect** and select **Session pooler**. Copy the actual host, port, database name, and username displayed there. Use the host exactly as shown; do not guess the pooler host.
5. In **Project Settings → API Keys** (wording may vary), obtain a server-side **secret key**. An older `service_role` key can also work. Never use a publishable/`anon` key for backend storage operations, and never expose the secret/service-role key to browser code.
6. Find the project URL, usually shaped like `https://<project-ref>.supabase.co`.

Do not paste any password or secret key into chat, source code, SQL scripts, GitHub, or screenshots.

## 2. Create the audit HMAC secret

In PowerShell on your own computer, run:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Copy the generated value into Render as `TRUSTVAULT_AUDIT_HMAC_KEY`. Keep that same value private and unchanged for every future deployment. Losing or replacing it can make existing audit-chain checkpoints unverifiable. This is a fresh hosted database, so it does not need to match the key used with the original Oracle database.

## 3. Push the deployment code to GitHub

The repository needs the hosted production profile, PostgreSQL driver, Supabase Storage adapter, and `supabase/schema.sql` from this version. After copying the updated files into your local repository, review the changes and run `mvn clean test package`. Do not push if tests fail or if credentials/private uploads appear in Git status.

Example commands from the repository root:

```powershell
git status --short
git diff --check
git add .gitignore .dockerignore pom.xml Dockerfile src supabase README.md THREATS.md PROMPTS.md DEPLOYMENT.md SUPABASE_DEPLOYMENT.md DEMO_SCRIPT.md WRITEUP.md GIT_COMMITS.md docs
git diff --cached --name-only
```

Review the staged files, then commit and push to `main`.

## 4. Create the Render Web Service

1. Open <https://dashboard.render.com/> and choose **New + → Web Service**.
2. Connect the existing GitHub repository and select branch `main`.
3. Set the service name to `docurithm`.
4. Leave **Root Directory** blank.
5. Select **Docker** runtime and set Dockerfile path to `./Dockerfile`.
6. Choose the Free instance for a demo if it is offered to your account. Free web services can sleep when idle and have an ephemeral application filesystem; this project therefore stores document bytes in Supabase Storage rather than the Render filesystem.
7. Add the environment variables below before deploying.

## 5. Add Render environment variables

### Supabase PostgreSQL connection

| Key | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require` using the exact host shown under Connect → Session pooler |
| `SPRING_DATASOURCE_USERNAME` | The exact Session pooler username shown by Supabase; usually `postgres.<project-ref>` |
| `SPRING_DATASOURCE_PASSWORD` | The database password you set when creating the Supabase project |

Do not put the database password inside `SPRING_DATASOURCE_URL`; keep it only in `SPRING_DATASOURCE_PASSWORD`.

### Supabase private Storage

| Key | Value |
|---|---|
| `SUPABASE_URL` | Your Supabase project URL, such as `https://<project-ref>.supabase.co` |
| `SUPABASE_SECRET_KEY` | A server-side `sb_secret_...` key from Supabase (preferred), or a legacy `service_role` JWT |
| `SUPABASE_STORAGE_BUCKET` | `docurithm-private` |
| `TRUSTVAULT_AUDIT_HMAC_KEY` | The random Base64 secret generated in Step 2 |

The bucket must remain private. The server-side secret key bypasses Storage RLS, so the application is responsible for checking document ownership/permissions before every read or download. It belongs only in Render's environment settings. Current `sb_secret_...` keys are sent in the `apikey` header only; the code uses a Bearer header only for a legacy JWT-format `service_role` key.

### Initial account seeding (recommended for the first demo)

The application creates the three initial accounts only if **all six** variables below are present. Use your own chosen emails and strong, unique passwords of at least 12 characters. Never use sample/default passwords.

- `TRUSTVAULT_ADMIN_EMAIL`
- `TRUSTVAULT_ADMIN_PASSWORD`
- `TRUSTVAULT_OWNER_EMAIL`
- `TRUSTVAULT_OWNER_PASSWORD`
- `TRUSTVAULT_VIEWER_EMAIL`
- `TRUSTVAULT_VIEWER_PASSWORD`

The app requires no manually configured `PORT`; Render supplies it. The production profile uses `${PORT:8081}`. Do not configure `ORACLE_DB_URL`, `ORACLE_DB_USERNAME`, `ORACLE_DB_PASSWORD`, or `TRUSTVAULT_STORAGE_DIR` for the hosted profile.

## 6. Deploy and verify

Click **Create Web Service** (or **Deploy**, depending on the dashboard). In the first deploy logs, check that the Docker image builds, the app connects to PostgreSQL, Hibernate validates the schema, and Spring Boot starts on Render's assigned port.

Then test the public HTTPS URL:

1. Sign in as each configured account.
2. Upload a small PDF or text file as the Owner.
3. Share it with the Viewer using `VIEW`, verify view/download, then revoke the permission and verify the next request is denied.
4. Replace a document's content with an `EDIT` recipient if that workflow is enabled.
5. Verify that the file remains accessible after a service redeploy and check the admin audit view/integrity endpoint.

If deploy logs show a missing table/sequence, rerun or inspect `supabase/schema.sql` in the correct new Supabase project and compare it with the error. If a connection fails, recheck the session-pooler host, port 5432, username, password, and `sslmode=require`.

## Limits and security notes

- The free Render web service may spin down after inactivity and takes time to wake up. Its local filesystem is ephemeral; uploaded document bytes are stored in Supabase Storage instead.
- Supabase Free has quotas and may pause an inactive project. Review the provider's current limits before relying on the service for important data.
- This is a hackathon/demo configuration, not a production SLA or compliance attestation.
- Never upload the local `trustvault-private-files` directory, `.git` folder, `target` build output, database exports, `.env` files, or secrets into a public repository.
- The original Oracle database is not changed, migrated, or deleted by these instructions.
