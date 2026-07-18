CREATE TABLE processing_uploads (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    extension VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    policy_version VARCHAR(64) NOT NULL
);

CREATE TABLE processing_jobs (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    upload_id UUID NOT NULL REFERENCES processing_uploads(id),
    job_type VARCHAR(64) NOT NULL,
    parameters_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress_percent INTEGER NOT NULL,
    message VARCHAR(1024) NOT NULL,
    error_code VARCHAR(64),
    error_message VARCHAR(1024),
    correlation_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE processing_artifacts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    job_id UUID NOT NULL REFERENCES processing_jobs(id) ON DELETE CASCADE,
    kind VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_processing_uploads_owner_expiry ON processing_uploads(owner_id, expires_at);
CREATE INDEX idx_processing_jobs_owner_status ON processing_jobs(owner_id, status);
CREATE INDEX idx_processing_jobs_expiry ON processing_jobs(expires_at);
CREATE INDEX idx_processing_artifacts_owner_expiry ON processing_artifacts(owner_id, expires_at);
