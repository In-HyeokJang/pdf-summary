package com.insightai.pdfsummary.dto

import com.insightai.pdfsummary.domain.PdfDocument
import java.time.LocalDateTime

data class PdfSummaryResponse(
    val id: Long,
    val fileName: String,
    val originLang: String?,
    val summary: String?,
    val translatedText: String?,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(doc: PdfDocument) = PdfSummaryResponse(
            id = doc.id,
            fileName = doc.fileName,
            originLang = doc.originLang,
            summary = doc.summary,
            translatedText = doc.translatedText,
            createdAt = doc.createdAt
        )
    }
}