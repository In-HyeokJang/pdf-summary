package com.insightai.pdfsummary.repository

import com.insightai.pdfsummary.domain.PdfDocument
import org.springframework.data.jpa.repository.JpaRepository

interface PdfDocumentRepository : JpaRepository<PdfDocument, Long> {
    fun findByFileHash(fileHash: String): PdfDocument?
}