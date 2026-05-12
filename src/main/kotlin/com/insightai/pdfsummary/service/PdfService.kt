package com.insightai.pdfsummary.service

import com.insightai.pdfsummary.domain.PdfDocument
import com.insightai.pdfsummary.dto.PdfSummaryResponse
import com.insightai.pdfsummary.dto.PdfUploadResponse
import com.insightai.pdfsummary.repository.PdfDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Flux
import java.security.MessageDigest

@Service
class PdfService(
    private val pdfParserService: PdfParserService,
    private val vllmService: VllmService,
    private val repository: PdfDocumentRepository
) {
    private val log = LoggerFactory.getLogger(PdfService::class.java)

    fun upload(file: MultipartFile, sourceLang: String): PdfUploadResponse {
        val fileBytes = file.bytes
        val fileHash = MessageDigest.getInstance("SHA-256")
            .digest(fileBytes)
            .joinToString("") { "%02x".format(it) }

        repository.findByFileHash(fileHash)?.let { existing ->
            log.info("중복 파일 감지 (hash: ${fileHash.take(8)}...) → 캐시 반환")
            return PdfUploadResponse(id = existing.id, fileName = existing.fileName, summary = existing.summary, status = "CACHED")
        }

        val tStart = System.currentTimeMillis()
        val originalText = pdfParserService.extractText(file)
        val chunks = pdfParserService.splitIntoChunks(originalText)
        log.info("[TIMING] 파일: ${file.originalFilename}, 총 ${originalText.length}자, 청크 수: ${chunks.size}, 추출: ${System.currentTimeMillis() - tStart}ms")

        val t0 = System.currentTimeMillis()
        val translatedChunks: List<String> = Flux.fromIterable(chunks.withIndex().toList())
            .flatMapSequential({ (i, chunk) ->
                log.info("[TIMING] 청크 번역 요청: ${i + 1}/${chunks.size} (${chunk.length}자)")
                vllmService.translateAsync(chunk, sourceLang)
                    .doOnSuccess { log.info("[TIMING] 청크 번역 완료: ${i + 1}/${chunks.size}") }
            }, chunks.size)
            .collectList()
            .block()!!
        val translatedText = translatedChunks.joinToString("\n\n")
        log.info("[TIMING] 번역 완료: ${(System.currentTimeMillis() - t0) / 1000}초 (${chunks.size}개 청크)")

        val t1 = System.currentTimeMillis()
        val summaryInput = translatedText.take(6000)
        log.info("[TIMING] 요약 입력: ${summaryInput.length}자 (번역본 전체의 ${String.format("%.0f", summaryInput.length * 100.0 / translatedText.length.coerceAtLeast(1))}%)")
        val summary = vllmService.summarize(summaryInput)
        log.info("[TIMING] 요약 완료: ${(System.currentTimeMillis() - t1) / 1000}초")
        log.info("[TIMING] 전체 소요: ${(System.currentTimeMillis() - tStart) / 1000}초")

        val saved = repository.save(
            PdfDocument(
                fileName = file.originalFilename ?: "unknown.pdf",
                fileHash = fileHash,
                originLang = sourceLang,
                originalText = originalText,
                translatedText = translatedText,
                summary = summary
            )
        )

        return PdfUploadResponse(
            id = saved.id,
            fileName = saved.fileName,
            summary = saved.summary,
            status = "SUCCESS"
        )
    }

    fun list(): List<PdfSummaryResponse> =
        repository.findAll().map { PdfSummaryResponse.from(it) }

    fun get(id: Long): PdfSummaryResponse =
        repository.findByIdOrNull(id)
            ?.let { PdfSummaryResponse.from(it) }
            ?: throw NoSuchElementException("Document not found: $id")
}