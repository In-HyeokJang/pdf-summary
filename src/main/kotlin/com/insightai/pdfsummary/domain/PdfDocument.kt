package com.insightai.pdfsummary.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "pdf_document")
class PdfDocument(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "file_name", nullable = false, length = 255)
    val fileName: String,

    @Column(name = "file_hash", length = 64)
    val fileHash: String?,

    @Column(name = "origin_lang", length = 10)
    val originLang: String?,

    @Column(name = "original_text", columnDefinition = "TEXT")
    var originalText: String? = null,

    @Column(name = "translated_text", columnDefinition = "TEXT")
    var translatedText: String? = null,

    @Column(name = "summary", columnDefinition = "TEXT")
    var summary: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    var status: ProcessingStatus = ProcessingStatus.PENDING,

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "processing_time_sec")
    var processingTimeSec: Long? = null
)