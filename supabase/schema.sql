-- Docurithm's fresh Supabase PostgreSQL schema.
-- Run this ONLY in the new, empty Supabase project created for Docurithm.
-- Do not run this script against the existing Oracle MYDATABASE schema.

CREATE SEQUENCE IF NOT EXISTS "TV_USERS_SEQ" START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS "TV_DOCUMENTS_SEQ" START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS "TV_DOCUMENT_PERMISSIONS_SEQ" START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS "TV_AUDIT_LOG_SEQ" START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS tv_users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role_name VARCHAR(20) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tv_users_email UNIQUE (email),
    CONSTRAINT ck_tv_users_role CHECK (role_name IN ('ADMIN', 'OWNER', 'VIEWER')),
    CONSTRAINT ck_tv_users_enabled CHECK (enabled IN (0, 1))
);

CREATE TABLE IF NOT EXISTS tv_documents (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(36) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_sha256 VARCHAR(64),
    owner_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    category VARCHAR(100) NOT NULL DEFAULT 'Uncategorized',
    CONSTRAINT uk_tv_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_tv_doc_owner FOREIGN KEY (owner_id) REFERENCES tv_users(id),
    CONSTRAINT ck_tv_doc_deleted CHECK (is_deleted IN (0, 1))
);

CREATE TABLE IF NOT EXISTS tv_document_permissions (
    id BIGINT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission_type VARCHAR(10) NOT NULL,
    granted_by BIGINT NOT NULL,
    granted_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tv_doc_permission UNIQUE (document_id, user_id),
    CONSTRAINT fk_tv_perm_doc FOREIGN KEY (document_id) REFERENCES tv_documents(id),
    CONSTRAINT fk_tv_perm_user FOREIGN KEY (user_id) REFERENCES tv_users(id),
    CONSTRAINT fk_tv_perm_grantor FOREIGN KEY (granted_by) REFERENCES tv_users(id),
    CONSTRAINT ck_tv_perm_type CHECK (permission_type IN ('VIEW', 'EDIT'))
);

CREATE TABLE IF NOT EXISTS tv_audit_logs (
    id BIGINT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    event_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    previous_hash VARCHAR(64),
    event_hash VARCHAR(64),
    target_user_id BIGINT,
    target_user_email VARCHAR(320),
    previous_permission_type VARCHAR(20),
    new_permission_type VARCHAR(20),
    CONSTRAINT fk_tv_audit_doc FOREIGN KEY (document_id) REFERENCES tv_documents(id),
    CONSTRAINT fk_tv_audit_user FOREIGN KEY (user_id) REFERENCES tv_users(id)
);

CREATE TABLE IF NOT EXISTS tv_audit_chain_head (
    id BIGINT PRIMARY KEY,
    last_event_id BIGINT,
    last_hash VARCHAR(64) NOT NULL,
    checkpoint_hmac VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    chain_start_after_id BIGINT,
    historical_baseline_hmac VARCHAR(64),
    CONSTRAINT ck_tv_chain_head_singleton CHECK (id = 1)
);

-- The Spring Boot server accesses these tables through JDBC. Browser clients must not
-- access application tables directly through the Supabase Data API. With RLS enabled
-- and no client policies defined here, client-role requests are denied by default.
ALTER TABLE tv_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE tv_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE tv_document_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE tv_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE tv_audit_chain_head ENABLE ROW LEVEL SECURITY;

CREATE INDEX IF NOT EXISTS idx_tv_doc_owner ON tv_documents(owner_id);
CREATE INDEX IF NOT EXISTS idx_tv_doc_category ON tv_documents(category);
CREATE INDEX IF NOT EXISTS idx_tv_doc_deleted ON tv_documents(is_deleted);
CREATE INDEX IF NOT EXISTS idx_tv_perm_lookup ON tv_document_permissions(document_id, user_id);
CREATE INDEX IF NOT EXISTS idx_tv_audit_doc ON tv_audit_logs(document_id);
CREATE INDEX IF NOT EXISTS idx_tv_audit_time ON tv_audit_logs(event_time DESC);

-- Seed only the single audit-chain control row; application events create the history.
INSERT INTO tv_audit_chain_head (
    id, last_event_id, last_hash, checkpoint_hmac, updated_at,
    chain_start_after_id, historical_baseline_hmac
)
VALUES (1, NULL, 'GENESIS', 'UNINITIALIZED', CURRENT_TIMESTAMP, NULL, NULL)
ON CONFLICT (id) DO NOTHING;
