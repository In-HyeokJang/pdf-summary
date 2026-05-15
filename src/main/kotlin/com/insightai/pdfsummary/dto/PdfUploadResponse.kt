package com.insightai.pdfsummary.dto

import com.insightai.pdfsummary.domain.ProcessMode
import com.insightai.pdfsummary.domain.ProcessingStatus

data class PdfUploadResponse(
    val id: Long,
    val fileName: String,
    val summary: String?,
    val status: ProcessingStatus,
    val processMode: ProcessMode
)