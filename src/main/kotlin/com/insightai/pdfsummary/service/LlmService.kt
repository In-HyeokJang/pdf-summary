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
    /** @param customPrompt null이면 하드코딩된 기본 시스템 프롬프트 사용 */
    fun translateAsync(chunk: String, sourceLang: String, customPrompt: String? = null): Mono<String>
    fun chunkSummarizeAsync(chunk: String): Mono<String>
    fun summarizeFromSourceAsync(chunk: String, sourceLang: String): Mono<String>
    /** @param customPrompt null이면 문서 유형 자동 판별 기본 요약 프롬프트 사용 */
    fun summarizeAsync(text: String, customPrompt: String? = null): Mono<String>
    fun summarize(text: String, customPrompt: String? = null): String = summarizeAsync(text, customPrompt).block() ?: ""
}