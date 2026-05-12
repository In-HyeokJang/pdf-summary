package com.insightai.pdfsummary.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "vllm")
data class VllmProperties(
    val defaultBaseUrl: String,
    val defaultModel: String,
    val languageModels: Map<String, LanguageModel> = emptyMap()
) {
    data class LanguageModel(val baseUrl: String, val model: String)

    fun resolve(lang: String): Pair<String, String> =
        languageModels[lang.uppercase()]
            ?.let { it.baseUrl to it.model }
            ?: (defaultBaseUrl to defaultModel)
}