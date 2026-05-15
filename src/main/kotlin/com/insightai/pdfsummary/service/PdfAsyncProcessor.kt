package com.insightai.pdfsummary.service

import com.insightai.pdfsummary.config.VllmProperties
import com.insightai.pdfsummary.domain.ProcessMode
import com.insightai.pdfsummary.domain.ProcessingStatus
import com.insightai.pdfsummary.repository.PdfDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.time.LocalDateTime

@Component
class PdfAsyncProcessor(
    private val pdfParserService: PdfParserService,
    private val vllmService: VllmService,
    private val repository: PdfDocumentRepository,
    private val vllmProperties: VllmProperties
) {
    private val log = LoggerFactory.getLogger(PdfAsyncProcessor::class.java)

    @Async("pdfTaskExecutor")
    fun processAsync(docId: Long, sourceLang: String) {
        val doc = repository.findByIdOrNull(docId) ?: run {
            log.error("[ASYNC] 문서 없음: id={}", docId)
            return
        }
        val originalText = doc.originalText ?: run {
            log.error("[ASYNC] originalText 없음: id={}", docId)
            doc.status = ProcessingStatus.FAILED
            doc.completedAt = LocalDateTime.now()
            repository.save(doc)
            return
        }

        val tStart = System.currentTimeMillis()
        try {
            when (doc.processMode) {
                ProcessMode.TRANSLATE -> doTranslate(docId, originalText, sourceLang, tStart)
                ProcessMode.SUMMARIZE -> doSummarize(docId, originalText, sourceLang, tStart)
                ProcessMode.BOTH      -> doTranslateAndSummarize(docId, originalText, sourceLang, tStart)
            }
        } catch (e: Exception) {
            log.error("[ASYNC] 처리 실패: id={}", docId, e)
            val doc2 = repository.findByIdOrNull(docId) ?: return
            doc2.status = ProcessingStatus.FAILED
            doc2.completedAt = LocalDateTime.now()
            doc2.processingTimeSec = (System.currentTimeMillis() - tStart) / 1000
            repository.save(doc2)
        }
    }

    private fun doTranslate(docId: Long, originalText: String, sourceLang: String, tStart: Long) {
        val translatedText = translateText(docId, originalText, sourceLang)
        val elapsed = (System.currentTimeMillis() - tStart) / 1000
        val doc = repository.findByIdOrNull(docId)!!
        doc.translatedText = translatedText
        doc.status = ProcessingStatus.DONE
        doc.completedAt = LocalDateTime.now()
        doc.processingTimeSec = elapsed
        repository.save(doc)
        log.info("[ASYNC] 번역 완료: id={}, {}초", docId, elapsed)
    }

    private fun doSummarize(docId: Long, originalText: String, sourceLang: String, tStart: Long) {
        val chunks = pdfParserService.splitIntoChunks(
            originalText, maxChunkSize = vllmProperties.summaryChunkSize, overlapSize = 0
        )
        log.info("[ASYNC] 원문 소요약 시작: id={}, {}청크", docId, chunks.size)
        val chunkSummaries: List<String> = Flux.fromIterable(chunks.withIndex().toList())
            .flatMapSequential({ (i, chunk) ->
                log.info("[ASYNC] 소요약 요청: {}/{}", i + 1, chunks.size)
                vllmService.summarizeFromSourceAsync(chunk, sourceLang)
                    .doOnSuccess { log.info("[ASYNC] 소요약 완료: {}/{}", i + 1, chunks.size) }
            }, vllmProperties.summaryConcurrency)
            .collectList()
            .block()!!

        val reducedInput = chunkSummaries.joinToString("\n\n").take(4000)
        val summary = vllmService.summarize(reducedInput)

        val elapsed = (System.currentTimeMillis() - tStart) / 1000
        val doc = repository.findByIdOrNull(docId)!!
        doc.summary = summary
        doc.status = ProcessingStatus.DONE
        doc.completedAt = LocalDateTime.now()
        doc.processingTimeSec = elapsed
        repository.save(doc)
        log.info("[ASYNC] 요약 완료: id={}, {}초", docId, elapsed)
    }

    private fun doTranslateAndSummarize(docId: Long, originalText: String, sourceLang: String, tStart: Long) {
        val translatedText = translateText(docId, originalText, sourceLang)

        val summaryChunks = pdfParserService.splitIntoChunks(
            translatedText, maxChunkSize = vllmProperties.summaryChunkSize, overlapSize = 0
        )
        log.info("[ASYNC] 소요약 시작: id={}, {}청크", docId, summaryChunks.size)
        val chunkSummaries: List<String> = Flux.fromIterable(summaryChunks.withIndex().toList())
            .flatMapSequential({ (i, chunk) ->
                log.info("[ASYNC] 소요약 요청: {}/{}", i + 1, summaryChunks.size)
                vllmService.chunkSummarizeAsync(chunk)
                    .doOnSuccess { log.info("[ASYNC] 소요약 완료: {}/{}", i + 1, summaryChunks.size) }
            }, vllmProperties.summaryConcurrency)
            .collectList()
            .block()!!

        val reducedInput = chunkSummaries.joinToString("\n\n").take(4000)
        val summary = vllmService.summarize(reducedInput)

        val elapsed = (System.currentTimeMillis() - tStart) / 1000
        val doc = repository.findByIdOrNull(docId)!!
        doc.translatedText = translatedText
        doc.summary = summary
        doc.status = ProcessingStatus.DONE
        doc.completedAt = LocalDateTime.now()
        doc.processingTimeSec = elapsed
        repository.save(doc)
        log.info("[ASYNC] 번역+요약 완료: id={}, {}초", docId, elapsed)
    }

    private fun translateText(docId: Long, originalText: String, sourceLang: String): String {
        val chunks = pdfParserService.splitIntoChunks(originalText)
        val concurrency = vllmProperties.translationConcurrency
        log.info("[ASYNC] 번역 시작: id={}, {}자, {}청크, concurrency={}", docId, originalText.length, chunks.size, concurrency)

        val translatedChunks: List<String> = Flux.fromIterable(chunks.withIndex().toList())
            .flatMapSequential({ (i, chunk) ->
                val t = System.currentTimeMillis()
                log.info("[ASYNC] 청크 번역 요청: {}/{} ({}자)", i + 1, chunks.size, chunk.length)
                vllmService.translateAsync(chunk, sourceLang)
                    .doOnSuccess { log.info("[ASYNC] 청크 번역 완료: {}/{} ({}ms)", i + 1, chunks.size, System.currentTimeMillis() - t) }
            }, concurrency)
            .collectList()
            .block()!!

        val translated = translatedChunks.joinToString("\n\n")
        log.info("[ASYNC] 번역 완료: id={}, {}자", docId, translated.length)
        return translated
    }
}