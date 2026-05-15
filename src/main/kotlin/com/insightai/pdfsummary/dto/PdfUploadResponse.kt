package com.insightai.pdfsummary.dto

import com.insightai.pdfsummary.domain.ProcessMode
import com.insightai.pdfsummary.domain.ProcessingStatus

/**
 * PDF 업로드 API(/api/pdf/upload, POST /upload)의 응답 DTO.
 *
 * 업로드는 HTTP 요청 내에서 즉시 반환되므로 처리가 완료되지 않은 상태로 응답된다.
 * - 신규 처리: status = PROCESSING, summary = null
 * - 중복 업로드: status = CACHED, 기존 문서의 id·summary 포함
 *
 * @property id 생성된 PdfDocument의 DB 기본키
 * @property fileName 업로드된 파일명
 * @property summary 처리 완료 전에는 null. CACHED 반환 시에는 기존 요약이 포함될 수 있음
 * @property status 현재 처리 상태 (PROCESSING 또는 CACHED)
 * @property processMode 사용자가 요청한 처리 모드
 */
data class PdfUploadResponse(
    val id: Long,
    val fileName: String,
    val summary: String?,
    val status: ProcessingStatus,
    val processMode: ProcessMode
)