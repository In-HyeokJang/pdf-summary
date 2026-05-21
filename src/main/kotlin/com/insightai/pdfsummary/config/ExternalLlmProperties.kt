package com.insightai.pdfsummary.config

import com.insightai.pdfsummary.domain.LlmProvider
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 외부 LLM API 설정.
 *
 * external-llm.provider=LOCAL(기본)이면 기존 vLLM 로컬 모델을 사용한다.
 * CLAUDE 또는 GEMINI로 설정하면 해당 API를 호출하고 llm_provider 필드와 함께 DB에 저장한다.
 * LOCAL 외 제공자 결과는 /api/pdf/training/export 로 JSONL 내보내기가 가능하다.
 *
 * @property provider 사용할 LLM 제공자 (기본: LOCAL)
 * @property translationConcurrency 외부 API 번역 청크 동시 요청 수. 무료 티어 rate limit에 맞춰 낮게 설정
 * @property claude Anthropic Claude API 연결 설정
 * @property gemini Google Gemini API 연결 설정
 */
@ConfigurationProperties(prefix = "external-llm")
data class ExternalLlmProperties(
    val provider: LlmProvider = LlmProvider.LOCAL,
    val translationConcurrency: Int = 2,
    val claude: ClaudeConfig = ClaudeConfig(),
    val gemini: GeminiConfig = GeminiConfig()
) {
    /** Anthropic Claude API 연결 설정. */
    data class ClaudeConfig(
        val apiKey: String = "",
        val model: String = "claude-sonnet-4-6"
    )

    /** Google Gemini API 연결 설정. */
    data class GeminiConfig(
        val apiKey: String = "",
        val model: String = "gemini-2.0-flash"
    )
}