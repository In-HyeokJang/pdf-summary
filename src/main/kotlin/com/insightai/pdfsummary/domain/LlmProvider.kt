package com.insightai.pdfsummary.domain

/**
 * PDF 처리에 사용한 LLM 제공자.
 *
 * LOCAL 외의 제공자로 생성된 결과는 LoRA 파인튜닝 학습 데이터로 활용되며,
 * /api/pdf/training/export API로 JSONL 형태로 내보낼 수 있다.
 *
 * @property displayName UI·로그에 표시할 한국어 레이블
 */
enum class LlmProvider(val displayName: String) {
    /** 로컬 vLLM 모델 (EXAONE/Qwen). 기본값. */
    LOCAL("로컬 vLLM"),

    /** Anthropic Claude API. claude-sonnet-4-6 등. */
    CLAUDE("Claude API"),

    /** Google Gemini API. gemini-2.0-flash 등. */
    GEMINI("Gemini API")
}