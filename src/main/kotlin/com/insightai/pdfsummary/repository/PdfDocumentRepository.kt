package com.insightai.pdfsummary.repository

import com.insightai.pdfsummary.domain.PdfDocument
import com.insightai.pdfsummary.domain.ProcessingStatus
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PdfDocumentRepository : JpaRepository<PdfDocument, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT p FROM PdfDocument p WHERE p.fileHash = :fileHash")
    fun findAllByFileHashForUpdate(@Param("fileHash") fileHash: String): List<PdfDocument>

    fun findByStatusAndStartedAtBefore(status: ProcessingStatus, before: LocalDateTime): List<PdfDocument>
}