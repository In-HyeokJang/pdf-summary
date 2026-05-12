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

    @Lob @Column(name = "original_text", columnDefinition = "TEXT")
    val originalText: String?,

    @Lob @Column(name = "translated_text", columnDefinition = "TEXT")
    val translatedText: String?,

    @Lob @Column(name = "summary", columnDefinition = "TEXT")
    val summary: String?,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)