package com.insightai.pdfsummary.service

import com.insightai.pdfsummary.config.ExternalLlmProperties
import com.insightai.pdfsummary.config.VllmProperties
import com.insightai.pdfsummary.domain.LlmProvider
import com.insightai.pdfsummary.domain.PdfDocument
import com.insightai.pdfsummary.domain.ProcessMode
import com.insightai.pdfsummary.domain.ProcessingStatus
import com.insightai.pdfsummary.dto.PdfSummaryResponse
import com.insightai.pdfsummary.dto.PdfUploadResponse
import com.insightai.pdfsummary.dto.TrainingRecord
import com.insightai.pdfsummary.repository.PdfDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.time.LocalDateTime

/**
 * PDF 업로드·조회·재시도의 진입점이 되는 핵심 서비스.
 *
 * 동기 흐름(HTTP 요청 내): SHA-256 해시 중복 확인 → 텍스트 추출 → DB 저장 → 즉시 반환.
 * 비동기 흐름: [PdfAsyncProcessor.processAsync]에 위임하여 백그라운드에서 번역·요약 수행.
 *
 * 중복 업로드는 비관적 락([PdfDocumentRepository.findAllByFileHashForUpdate])으로 경쟁 조건을 방지하며,
 * 이미 완료된 결과가 있으면 CACHED 상태로 즉시 반환한다.
 */
@Service
class PdfService(
    private val pdfParserService: PdfParserService,
    private val asyncProcessor: PdfAsyncProcessor,
    private val repository: PdfDocumentRepository,
    private val vllmProperties: VllmProperties,
    private val externalLlmProperties: ExternalLlmProperties,
    txManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(PdfService::class.java)
    private val tx = TransactionTemplate(txManager)

    /**
     * PDF 파일을 업로드하고 비동기 처리를 시작한다.
     *
     * 동일 파일 해시 + 동일 processMode의 완료 문서가 있으면 CACHED 상태로 즉시 반환한다.
     * 번역본만 있고 SUMMARIZE/BOTH 모드 요청 시 fast-track으로 번역 단계를 건너뛴다.
     *
     * @param file 업로드된 PDF 파일 (최대 100MB)
     * @param sourceLang 원문 언어 코드 (EN / JA / ZH)
     * @param processMode 처리 모드 (기본값: BOTH)
     * @return 처리 접수 또는 캐시 결과를 담은 [PdfUploadResponse]
     * @throws IllegalStateException 동시 중복 업로드 락 획득 실패 시
     */
    fun upload(
        file: MultipartFile,
        sourceLang: String,
        processMode: ProcessMode = ProcessMode.BOTH,
        customTranslatePrompt: String? = null,
        customSummaryPrompt: String? = null
    ): PdfUploadResponse {
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
                        llmProvider = externalLlmProperties.provider,
                        status = ProcessingStatus.PROCESSING,
                         startedAt = LocalDateTime.now(),
                        customTranslatePrompt = customTranslatePrompt?.takeIf { it.isNotBlank() },
                        customSummaryPrompt = customSummaryPrompt?.takeIf { it.isNotBlank() }
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

    /**
     * FAILED 상태 문서를 재처리한다.
     *
     * 저장된 [PdfDocument.originalText]를 재사용하므로 파일을 다시 업로드할 필요가 없다.
     * FAILED 상태가 아닌 문서에 호출하면 [IllegalArgumentException]이 발생한다.
     *
     * @param id 재처리할 문서의 DB 기본키
     * @return PROCESSING 상태의 [PdfUploadResponse]
     * @throws NoSuchElementException 해당 id가 존재하지 않는 경우
     * @throws IllegalArgumentException FAILED 상태가 아닌 경우
     */
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

    /**
     * PROCESSING 상태가 [VllmProperties.staleTimeoutMinutes]분을 초과한 문서를 FAILED로 전환한다.
     *
     * 1분마다 실행되며, 애플리케이션 재시작 전에 시작된 작업이나 예기치 않은 스레드 종료로
     * 영구 PROCESSING 상태에 남는 문서를 정리한다.
     */
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
        repository.findAllByOrderByCreatedAtDesc().map { PdfSummaryResponse.from(it) }

    fun get(id: Long): PdfSummaryResponse =
        repository.findByIdOrNull(id)
            ?.let { PdfSummaryResponse.from(it) }
            ?: throw NoSuchElementException("Document not found: $id")

    /**
     * DONE 상태이면서 LOCAL 외의 제공자(Claude/Gemini)로 생성된 결과를 학습 데이터로 반환한다.
     *
     * 반환된 [TrainingRecord] 목록은 컨트롤러에서 JSONL로 직렬화해 내보낸다.
     * 하나의 PdfDocument가 번역과 요약 둘 다 있는 경우 두 개의 레코드로 분리된다.
     */
    fun exportTrainingData(): List<TrainingRecord> {
        val docs = repository.findByStatusAndLlmProviderNot(ProcessingStatus.DONE, LlmProvider.LOCAL)
        return docs.flatMap { doc ->
            val lang = doc.originLang ?: "EN"
            val langLabel = when (lang.uppercase()) { "EN" -> "영어"; "ZH" -> "중국어"; "JA" -> "일본어"; else -> lang }
            val records = mutableListOf<TrainingRecord>()

            if (doc.translatedText != null && doc.originalText != null) {
                records += TrainingRecord(
                    id = doc.id,
                    task = "translate",
                    lang = lang,
                    instruction = "다음 ${langLabel} 텍스트를 한국어로 번역하세요.",
                    input = doc.originalText!!,
                    output = doc.translatedText!!,
                    provider = doc.llmProvider.name
                )
            }
            if (doc.summary != null && doc.originalText != null) {
                records += TrainingRecord(
                    id = doc.id,
                    task = "summarize",
                    lang = lang,
                    instruction = "다음 문서를 한국어로 요약하세요.",
                    input = doc.originalText!!,
                    output = doc.summary!!,
                    provider = doc.llmProvider.name
                )
            }
            records
        }
    }
}