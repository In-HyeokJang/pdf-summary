package com.insightai.pdfsummary.dto

import com.insightai.pdfsummary.domain.PdfDocument
import com.insightai.pdfsummary.domain.ProcessMode
import com.insightai.pdfsummary.domain.ProcessingStatus
import java.time.LocalDateTime

data class PdfSummaryResponse(
    val id: Long,
    val fileName: String,
    val originLang: String?,
    val summary: String?,
    val translatedText: String?,
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val processingTimeSec: Long?,
    val status: ProcessingStatus,
    val processMode: ProcessMode
) {
    companion object {
        fun from(doc: PdfDocument) = PdfSummaryResponse(
            id = doc.id,
            fileName = doc.fileName,
            originLang = doc.originLang,
            summary = doc.summary,
            translatedText = doc.translatedText,
            createdAt = doc.createdAt,
            completedAt = doc.completedAt,
            processingTimeSec = doc.processingTimeSec,
            status = doc.status,
            processMode = doc.processMode
        )
    }
}