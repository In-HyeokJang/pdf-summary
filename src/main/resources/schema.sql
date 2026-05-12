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