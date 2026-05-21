package com.insightai.pdfsummary.service

import reactor.core.publisher.Mono

/**
 * LLM 번역·요약 공통 인터페이스.
 *
 * LOCAL(VllmService), CLAUDE(ClaudeService), GEMINI(GeminiService) 세 구현체가 있으며,
 * ExternalLlmProperties.provider 설정에 따라 PdfAsyncProcessor 가 적절한 구현체를 선택한다.
 * summarize(blocking)는 default 구현을 제공하므로 각 구현체에서 오버라이드 불필요.
 */
interface LlmService {
    fun translateAsync(chunk: String, sourceLang: String): Mono<String>
    fun chunkSummarizeAsync(chunk: String): Mono<String>
    fun summarizeFromSourceAsync(chunk: String, sourceLang: String): Mono<String>
    fun summarizeAsync(text: String): Mono<String>
    fun summarize(text: String): String = summarizeAsync(text).block() ?: ""
}