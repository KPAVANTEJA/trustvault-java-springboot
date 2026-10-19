# Database Setup

## Oracle Connection

Configure these environment variables for the application run configuration:

- `ORACLE_DB_URL`, for example
  `jdbc:oracle:thin:@//localhost:1521/YOUR_SERVICE_NAME`
- `ORACLE_DB_USERNAME`
- `ORACLE_DB_PASSWORD`

Replace the example service name with the actual Oracle service name.

## Initial User Accounts

Set all six `TRUSTVAULT_*` account environment variables described in the
README. The initializer stores BCrypt password hashes, not raw passwords.
It does not overwrite existing users, and it does not create default
accounts when credentials are absent.

## Initial Table

The first entity maps to `TV_USERS`, with these fields:

- `ID`
- `EMAIL`
- `FULL_NAME`
- `PASSWORD_HASH`
- `ROLE_NAME`
- `ENABLED`
- `CREATED_AT`

Local development uses Hibernate schema updates. Production schema changes
should move to reviewed, versioned database migrations before deployment.