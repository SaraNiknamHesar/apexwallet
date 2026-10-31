CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until TIMESTAMPTZ
);

CREATE TABLE refresh_sessions (
    token_hash CHAR(64) PRIMARY KEY,
    family_id UUID NOT NULL,
    username VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX refresh_sessions_family_idx ON refresh_sessions (family_id);

CREATE TABLE token_denylist (
    jti VARCHAR(80) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE idempotency_keys (
    owner_ref VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    response_status INT,
    response_body TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (owner_ref, idempotency_key)
);

CREATE TABLE pending_approvals (
    id UUID PRIMARY KEY,
    maker VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    at TIMESTAMPTZ NOT NULL,
    detail TEXT NOT NULL
);

CREATE OR REPLACE FUNCTION forbid_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION forbid_audit_mutation();
