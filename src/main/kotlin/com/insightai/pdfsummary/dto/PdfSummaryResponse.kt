package com.insightai.pdfsummary.dto

import com.insightai.pdfsummary.domain.PdfDocument
import com.insightai.pdfsummary.domain.ProcessMode
import com.insightai.pdfsummary.domain.ProcessingStatus
import java.time.LocalDateTime

/**
 * PDF 문서 조회 API(/api/pdf/list, /api/pdf/{id})와 Thymeleaf 뷰에서 공통으로 사용하는 응답 DTO.
 *
 * PdfDocument 엔티티를 직접 뷰에 노출하지 않고 이 DTO로 변환하여 전달한다.
 * 목록 페이지와 상세 페이지 모두 이 타입을 사용하므로 translatedText 같은
 * 대용량 필드도 포함되어 있다 (목록에서는 표시하지 않음).
 *
 * @property id DB 기본키
 * @property fileName 원본 파일명
 * @property originLang 원본 언어 코드 (EN / JA / ZH)
 * @property summary Map-Reduce 요약 결과. SUMMARIZE·BOTH 모드 완료 후에만 존재
 * @property translatedText 한국어 번역 전문. TRANSLATE·BOTH 모드 완료 후에만 존재
 * @property createdAt 레코드 생성 시각
 * @property completedAt 처리 완료 또는 실패 시각. 처리 중에는 null
 * @property processingTimeSec 처리 소요 시간(초). 완료 전에는 null
 * @property status 현재 처리 상태
 * @property processMode 처리 모드
 */
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
        /**
         * PdfDocument 엔티티를 PdfSummaryResponse DTO로 변환한다.
         *
         * @param doc 변환할 JPA 엔티티
         */
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