package com.insightai.pdfsummary.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `application.yaml`의 `vllm:` 섹션을 바인딩하는 설정 프로퍼티.
 *
 * 언어별로 다른 vLLM 엔드포인트·모델을 사용하기 위해 [languageModels] 맵을 두었다.
 * 맵에 없는 언어는 [defaultBaseUrl]/[defaultModel]로 폴백된다.
 *
 * Spring Boot는 환경변수 맵 키를 소문자로 정규화하므로, [resolve]는 대소문자 무관 조회를 한다.
 * 예) `VLLM_LANGUAGE_MODELS_EN_BASE_URL` → 맵 키 `"en"`.
 *
 * @property defaultBaseUrl 언어 매핑이 없을 때 사용하는 기본 vLLM 서버 주소
 * @property defaultModel 언어 매핑이 없을 때 사용하는 기본 모델 ID
 * @property languageModels 언어 코드(소문자) → (baseUrl, model) 매핑. 예) `en`, `ja`, `zh`
 * @property translationConcurrency 번역 병렬 청크 수 (flatMapSequential concurrency)
 * @property summaryChunkSize 소요약(Map 단계) 입력 최대 문자 수
 * @property summaryConcurrency 소요약 병렬 청크 수
 * @property staleTimeoutMinutes PROCESSING 상태가 이 분 이상 지속되면 스케줄러가 FAILED 처리
 */
@ConfigurationProperties(prefix = "vllm")
data class VllmProperties(
    val defaultBaseUrl: String,
    val defaultModel: String,
    val languageModels: Map<String, LanguageModel> = emptyMap(),
    val translationConcurrency: Int = 10,
    val summaryChunkSize: Int = 5000,
    val summaryConcurrency: Int = 4,
    val staleTimeoutMinutes: Int = 30
) {
    /** 언어별 vLLM 엔드포인트·모델 쌍. */
    data class LanguageModel(val baseUrl: String, val model: String)

    /**
     * 언어 코드에 대응하는 (baseUrl, model) 쌍을 반환한다.
     *
     * Spring Boot가 환경변수 맵 키를 소문자로 변환하므로 대소문자 무관 비교를 사용한다.
     * 맵에 없는 언어는 [defaultBaseUrl]/[defaultModel] 쌍으로 폴백된다.
     *
     * @param lang 언어 코드 (EN / JA / ZH 등, 대소문자 무관)
     * @return Pair(baseUrl, model)
     */
    fun resolve(lang: String): Pair<String, String> =
        languageModels.entries.firstOrNull { it.key.equals(lang, ignoreCase = true) }
            ?.value?.let { it.baseUrl to it.model }
            ?: (defaultBaseUrl to defaultModel)
}