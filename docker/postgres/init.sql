-- BioSecure: mirror schema used by the load-test PostgreSQL worker.
-- Applied by the postgres container on first start and re-applied by
-- scripts/postgres_worker.js (every statement is idempotent).

CREATE TABLE IF NOT EXISTS attendances (
    attendance_id    TEXT PRIMARY KEY,          -- Firestore document id
    user_id          TEXT NOT NULL,
    company_id       TEXT NOT NULL,
    sede_id          TEXT NOT NULL DEFAULT '',
    user_name        TEXT NOT NULL,
    attendance_date  DATE NOT NULL,
    check_in         TIME NOT NULL,
    check_out        TIME,
    status           TEXT NOT NULL,
    scan_type        TEXT NOT NULL,
    location         TEXT,
    latitude         DOUBLE PRECISION,
    longitude        DOUBLE PRECISION,
    accuracy         DOUBLE PRECISION,
    distance_meters  INTEGER,
    request_id       TEXT,
    marked_by        TEXT,
    client_mock_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ,
    synced_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Deliberately NOT unique: a duplicated pair must be visible to the verifier
-- instead of being silently merged by the upsert.
CREATE INDEX IF NOT EXISTS idx_attendances_user_date
    ON attendances (user_id, attendance_date);

CREATE INDEX IF NOT EXISTS idx_attendances_company_date
    ON attendances (company_id, attendance_date);

CREATE TABLE IF NOT EXISTS sync_runs (
    id                  BIGSERIAL PRIMARY KEY,
    source              TEXT NOT NULL,
    firestore_documents INTEGER NOT NULL,
    inserted            INTEGER NOT NULL,
    updated             INTEGER NOT NULL,
    skipped             INTEGER NOT NULL,
    postgres_rows       INTEGER NOT NULL,
    duplicate_keys      INTEGER NOT NULL,
    ran_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
