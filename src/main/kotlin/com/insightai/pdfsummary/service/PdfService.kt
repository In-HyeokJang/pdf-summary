package com.insightai.pdfsummary.service

import com.insightai.pdfsummary.domain.PdfDocument
import com.insightai.pdfsummary.domain.ProcessingStatus
import com.insightai.pdfsummary.dto.PdfSummaryResponse
import com.insightai.pdfsummary.dto.PdfUploadResponse
import com.insightai.pdfsummary.repository.PdfDocumentRepository
import org.slf4j.LoggerFactory
import com.insightai.pdfsummary.config.VllmProperties
import org.springframework.dao.DataAccessException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.time.LocalDateTime

@Service
class PdfService(
    private val pdfParserService: PdfParserService,
    private val asyncProcessor: PdfAsyncProcessor,
    private val repository: PdfDocumentRepository,
    private val vllmProperties: VllmProperties,
    txManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(PdfService::class.java)
    private val tx = TransactionTemplate(txManager)

    fun upload(file: MultipartFile, sourceLang: String): PdfUploadResponse {
        val fileBytes = file.bytes
        val fileHash = MessageDigest.getInstance("SHA-256")
            .digest(fileBytes)
            .joinToString("") { "%02x".format(it) }

        val (doc, cached) = try {
            tx.execute {
                val existing = repository.findByFileHashForUpdate(fileHash)
                if (existing != null) {
                    existing to true
                } else {
                    repository.save(
                        PdfDocument(
                            fileName = file.originalFilename ?: "unknown.pdf",
                            fileHash = fileHash,
                            originLang = sourceLang,
                            status = ProcessingStatus.PROCESSING,
                            startedAt = LocalDateTime.now()
                        )
                    ) to false
                }
            }!!
        } catch (e: DataAccessException) {
            log.warn("파일 락 획득 실패 (hash: {}...): {}", fileHash.take(8), e.message)
            throw IllegalStateException("처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요.")
        }

        if (cached) {
            log.info("중복 파일 감지 (hash: ${fileHash.take(8)}...) → 캐시 반환")
            return PdfUploadResponse(id = doc.id, fileName = doc.fileName, summary = doc.summary, status = ProcessingStatus.CACHED)
        }

        val tStart = System.currentTimeMillis()
        return try {
            val originalText = pdfParserService.extractText(file)
            log.info("[UPLOAD] 텍스트 추출 완료: ${file.originalFilename}, ${originalText.length}자, ${System.currentTimeMillis() - tStart}ms")
            doc.originalText = originalText
            repository.save(doc)
            asyncProcessor.processAsync(doc.id, sourceLang)
            PdfUploadResponse(id = doc.id, fileName = doc.fileName, summary = null, status = ProcessingStatus.PROCESSING)
        } catch (e: Exception) {
            log.error("텍스트 추출 실패: ${file.originalFilename}", e)
            markFailed(doc, tStart)
            throw e
        }
    }

    fun retry(id: Long): PdfUploadResponse {
        val doc = repository.findByIdOrNull(id)
            ?: throw NoSuchElementException("Document not found: $id")

        require(doc.status == ProcessingStatus.FAILED) {
            "FAILED 상태인 경우에만 재시도 가능합니다."
        }
        require(doc.originalText != null) {
            "원본 텍스트가 없어 재시도할 수 없습니다."
        }

        val sourceLang = doc.originLang ?: "EN"
        doc.status = ProcessingStatus.PROCESSING
        doc.startedAt = LocalDateTime.now()
        doc.completedAt = null
        doc.processingTimeSec = null
        repository.save(doc)

        log.info("[RETRY] id={}, file={}, lang={}", doc.id, doc.fileName, sourceLang)
        asyncProcessor.processAsync(doc.id, sourceLang)
        return PdfUploadResponse(id = doc.id, fileName = doc.fileName, summary = null, status = ProcessingStatus.PROCESSING)
    }

    private fun markFailed(doc: PdfDocument, tStart: Long) {
        doc.status = ProcessingStatus.FAILED
        doc.completedAt = LocalDateTime.now()
        doc.processingTimeSec = (System.currentTimeMillis() - tStart) / 1000
        repository.save(doc)
    }

    @Scheduled(fixedDelay = 60_000)
    fun recoverStaleProcessing() {
        val threshold = LocalDateTime.now().minusMinutes(vllmProperties.staleTimeoutMinutes.toLong())
        val stale = repository.findByStatusAndStartedAtBefore(ProcessingStatus.PROCESSING, threshold)
        if (stale.isEmpty()) return

        stale.forEach { doc ->
            doc.status = ProcessingStatus.FAILED
            doc.completedAt = LocalDateTime.now()
            repository.save(doc)
            log.warn("[RECOVERY] PROCESSING 장기 미완료 → FAILED: id={}, file={}, startedAt={}", doc.id, doc.fileName, doc.startedAt)
        }
    }

    fun list(): List<PdfSummaryResponse> =
        repository.findAll().map { PdfSummaryResponse.from(it) }

    fun get(id: Long): PdfSummaryResponse =
        repository.findByIdOrNull(id)
            ?.let { PdfSummaryResponse.from(it) }
            ?: throw NoSuchElementException("Document not found: $id")
}