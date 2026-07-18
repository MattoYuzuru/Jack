ALTER TABLE processing_uploads ADD COLUMN parser_route VARCHAR(64) NOT NULL DEFAULT 'binary';

CREATE INDEX idx_processing_uploads_route_expiry
    ON processing_uploads(parser_route, expires_at);
