package com.insightai.pdfsummary.dto

data class PdfUploadResponse(
    val id: Long,
    val fileName: String,
    val summary: String?,
    val status: String
)