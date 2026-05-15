package com.insightai.pdfsummary.service

import com.insightai.pdfsummary.domain.PdfDocument
import com.insightai.pdfsummary.domain.ProcessMode
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

    fun upload(file: MultipartFile, sourceLang: String, processMode: ProcessMode = ProcessMode.BOTH): PdfUploadResponse {
        val fileBytes = file.bytes
        val fileHash = MessageDigest.getInstance("SHA-256")
            .digest(fileBytes)
            .joinToString("") { "%02x".format(it) }

        data class LockResult(val doc: PdfDocument, val action: String, val reuseTranslation: String?, val reuseOriginal: String?)

        val lockResult = try {
            tx.execute {
                val all = repository.findAllByFileHashForUpdate(fileHash)
                val done = all.filter { it.status == ProcessingStatus.DONE || it.status == ProcessingStatus.CACHED }

                // 이미 같은 결과가 있으면 CACHED
                val cached = when (processMode) {
                    ProcessMode.TRANSLATE -> done.firstOrNull { it.translatedText != null }
                    ProcessMode.SUMMARIZE -> done.firstOrNull { it.summary != null }
                    ProcessMode.BOTH      -> done.firstOrNull { it.translatedText != null && it.summary != null }
                }
                if (cached != null) return@execute LockResult(cached, "CACHED", null, null)

                // 번역 데이터가 이미 있으면 fast-track (번역 단계 스킵)
                val withTranslation = if (processMode != ProcessMode.TRANSLATE) {
                    done.firstOrNull { it.translatedText != null }
                } else null

                val newDoc = repository.save(
                    PdfDocument(
                        fileName = file.originalFilename ?: "unknown.pdf",
                        fileHash = fileHash,
                        originLang = sourceLang,
                        processMode = processMode,
                        status = ProcessingStatus.PROCESSING,
                        startedAt = LocalDateTime.now()
                    )
                )
                val action = if (withTranslation != null) "FAST_TRACK" else "NEW"
                LockResult(newDoc, action, withTranslation?.translatedText, withTranslation?.originalText)
            }!!
        } catch (e: DataAccessException) {
            log.warn("파일 락 획득 실패 (hash: {}...): {}", fileHash.take(8), e.message)
            throw IllegalStateException("처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요.")
        }

        if (lockResult.action == "CACHED") {
            log.info("캐시 반환: hash={}, mode={}, docId={}", fileHash.take(8), processMode, lockResult.doc.id)
            return PdfUploadResponse(id = lockResult.doc.id, fileName = lockResult.doc.fileName, summary = lockResult.doc.summary, status = ProcessingStatus.CACHED, processMode = lockResult.doc.processMode)
        }

        val tStart = System.currentTimeMillis()
        val doc = lockResult.doc
        return try {
            // originalText: fast-track이면 기존 문서에서 복사, 아니면 파일에서 추출
            val originalText = if (lockResult.reuseOriginal != null) {
                log.info("[UPLOAD] 원문 재사용: ${file.originalFilename}")
                lockResult.reuseOriginal
            } else {
                val text = pdfParserService.extractText(file)
                log.info("[UPLOAD] 텍스트 추출 완료: ${file.originalFilename}, ${text.length}자, ${System.currentTimeMillis() - tStart}ms")
                text
            }
            doc.originalText = originalText

            if (lockResult.reuseTranslation != null) {
                doc.translatedText = lockResult.reuseTranslation
                log.info("[UPLOAD] 번역 재사용 → 요약 단계만 실행: docId={}", doc.id)
            }

            repository.save(doc)
            asyncProcessor.processAsync(doc.id, sourceLang)
            PdfUploadResponse(id = doc.id, fileName = doc.fileName, summary = null, status = ProcessingStatus.PROCESSING, processMode = processMode)
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

        log.info("[RETRY] id={}, file={}, lang={}, mode={}", doc.id, doc.fileName, sourceLang, doc.processMode)
        asyncProcessor.processAsync(doc.id, sourceLang)
        return PdfUploadResponse(id = doc.id, fileName = doc.fileName, summary = null, status = ProcessingStatus.PROCESSING, processMode = doc.processMode)
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