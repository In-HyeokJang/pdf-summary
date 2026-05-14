package com.insightai.pdfsummary.config

import org.springframework.boot.context.properties.ConfigurationProperties

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
    data class LanguageModel(val baseUrl: String, val model: String)

    fun resolve(lang: String): Pair<String, String> =
        languageModels[lang.uppercase()]
            ?.let { it.baseUrl to it.model }
            ?: (defaultBaseUrl to defaultModel)
}