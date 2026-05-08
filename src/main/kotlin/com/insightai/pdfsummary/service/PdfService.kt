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

        val originalText = pdfParserService.extractText(file)
        val chunks = pdfParserService.splitIntoChunks(originalText)
        log.info("총 ${chunks.size}개 청크 병렬 번역 시작 (총 ${originalText.length}자)")

        val translatedChunks: List<String> = Flux.fromIterable(chunks.withIndex().toList())
            .flatMapSequential({ (i, chunk) ->
                log.info("청크 번역 요청: ${i + 1}/${chunks.size}")
                vllmService.translateAsync(chunk, sourceLang)
                    .doOnSuccess { log.info("청크 번역 완료: ${i + 1}/${chunks.size}") }
            }, chunks.size)
            .collectList()
            .block()!!
        val translatedText = translatedChunks.joinToString("\n")

        log.info("청크별 요약 시작...")
        val chunkSummaries: List<String> = Flux.fromIterable(translatedChunks.withIndex().toList())
            .flatMapSequential({ (i, translated) ->
                log.info("청크 요약 요청: ${i + 1}/${translatedChunks.size}")
                vllmService.summarizeAsync(translated)
                    .doOnSuccess { log.info("청크 요약 완료: ${i + 1}/${translatedChunks.size}") }
            }, translatedChunks.size)
            .collectList()
            .block()!!

        log.info("최종 요약 생성 중...")
        val summaryInput = chunkSummaries.joinToString("\n").take(8000) // ~4000 tokens 안전 범위
        val summary = vllmService.summarize(summaryInput)
        log.info("요약 완료.")

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