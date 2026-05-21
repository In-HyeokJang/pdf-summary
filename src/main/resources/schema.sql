CREATE TABLE IF NOT EXISTS pdf_document (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    file_hash       VARCHAR(64),
    origin_lang     VARCHAR(10),
    original_text   TEXT,
    translated_text TEXT,
    summary         TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

ALTER TABLE pdf_document ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);
ALTER TABLE pdf_document ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'DONE';
ALTER TABLE pdf_document ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;
ALTER TABLE pdf_document ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
ALTER TABLE pdf_document ADD COLUMN IF NOT EXISTS processing_time_sec BIGINT;
ALTER TABLE pdf_document ADD COLUMN IF NOT EXISTS process_mode VARCHAR(20) NOT NULL DEFAULT 'BOTH';
ALTER TABLE pdf_document ADD COLUMN IF NOT EXISTS llm_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';