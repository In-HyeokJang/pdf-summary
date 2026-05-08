CREATE TABLE IF NOT EXISTS pdf_document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    file_hash       VARCHAR(64),
    origin_lang     VARCHAR(10),
    original_text   LONGTEXT,
    translated_text LONGTEXT,
    summary         TEXT,
    created_at      DATETIME DEFAULT NOW()
);

ALTER TABLE pdf_document ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);