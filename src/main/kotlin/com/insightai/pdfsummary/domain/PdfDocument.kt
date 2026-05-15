package com.insightai.pdfsummary.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * PDF 문서 하나의 처리 이력을 저장하는 JPA 엔티티.
 *
 * 동일 파일(fileHash)이라도 processMode가 다르면 별도 레코드로 저장된다.
 * 예) 같은 파일을 번역 요청 → TRANSLATE 레코드, 요약 요청 → SUMMARIZE 레코드.
 *
 * 변경 가능 필드(var): 비동기 처리 완료 후 PdfAsyncProcessor 가 갱신한다.
 * 불변 필드(val): 업로드 시점에 확정되며 이후 변경되지 않는다.
 */
@Entity
@Table(name = "pdf_document")
class PdfDocument(
    /** DB 자동 생성 기본키 (BIGINT GENERATED ALWAYS AS IDENTITY). */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 업로드된 원본 파일명. */
    @Column(name = "file_name", nullable = false, length = 255)
    val fileName: String,

    /**
     * 파일 바이트 전체의 SHA-256 해시 (16진수 64자).
     * 중복 업로드 감지에 사용되며 비관적 락(FOR UPDATE)과 함께 조회된다.
     */
    @Column(name = "file_hash", length = 64)
    val fileHash: String?,

    /** 원본 문서의 언어 코드. EN / JA / ZH 중 하나. */
    @Column(name = "origin_lang", length = 10)
    val originLang: String?,

    /** PDFBox로 추출한 원문 텍스트. 재시도·fast-track 시 재사용된다. */
    @Column(name = "original_text", columnDefinition = "TEXT")
    var originalText: String? = null,

    /** 한국어로 번역된 전문 텍스트. TRANSLATE·BOTH 모드에서만 생성된다. */
    @Column(name = "translated_text", columnDefinition = "TEXT")
    var translatedText: String? = null,

    /**
     * Map-Reduce 방식으로 생성된 최종 요약 텍스트.
     * SUMMARIZE·BOTH 모드에서만 생성된다.
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    var summary: String? = null,

    /** 레코드 최초 생성 시각. */
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /** 사용자가 선택한 처리 모드. DB에 문자열로 저장된다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "process_mode", length = 20, nullable = false)
    val processMode: ProcessMode = ProcessMode.BOTH,

    /** 현재 처리 상태. 비동기 처리 진행에 따라 갱신된다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    var status: ProcessingStatus = ProcessingStatus.PENDING,

    /** 비동기 처리가 시작된 시각. 스테일 타임아웃 판단 기준이 된다. */
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    /** 처리가 완료(DONE) 또는 실패(FAILED)된 시각. */
    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    /** 처리에 소요된 총 시간(초). 상세 페이지에 표시된다. */
    @Column(name = "processing_time_sec")
    var processingTimeSec: Long? = null
)