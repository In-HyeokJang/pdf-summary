package com.insightai.pdfsummary.service

import com.insightai.pdfsummary.config.VllmProperties
import com.insightai.pdfsummary.config.WebClientConfig
import com.insightai.pdfsummary.dto.VllmRequest
import com.insightai.pdfsummary.dto.VllmResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

@Service
class VllmService(
    private val properties: VllmProperties,
    private val webClientConfig: WebClientConfig
) {
    private val log = LoggerFactory.getLogger(VllmService::class.java)
    private val webClients = ConcurrentHashMap<String, WebClient>()

    private fun webClientFor(baseUrl: String): WebClient =
        webClients.getOrPut(baseUrl) { webClientConfig.buildWebClient(baseUrl) }

    fun translateAsync(chunk: String, sourceLang: String): Mono<String> {
        val (baseUrl, model) = properties.resolve(sourceLang)
        log.debug("번역 모델: {} ({})", model, sourceLang)
        return callTranslate(webClientFor(baseUrl), model, chunk, sourceLang, retry = false)
            .flatMap { result ->
                if (result.count { it.code in 0x4E00..0x9FFF } > 15) {
                    log.warn("CJK 감지 → 재시도 (lang={})", sourceLang)
                    callTranslate(webClientFor(baseUrl), model, chunk, sourceLang, retry = true)
                } else {
                    Mono.just(result)
                }
            }
    }

    private fun callTranslate(
        webClient: WebClient, model: String, chunk: String, sourceLang: String, retry: Boolean
    ): Mono<String> {
        val (system, user) = if (!retry) {
            "You are a translator. Translate the given text into Korean. Output only the Korean translation. Do not include any Chinese characters, Japanese characters, or meta-comments. If you detect a table (rows with aligned columns), format it as a markdown table with Korean headers. Figure/Table captions like \"Figure 1.\", \"Table 2.\" → translate to \"그림 1.\", \"표 2.\". Do not translate model names, dataset names, or metric names (e.g. BLEU, COMET, Llama)." to
            "Translate this $sourceLang text into Korean:\n\n$chunk"
        } else {
            "번역가입니다. 주어진 텍스트를 한국어로 번역하세요. 한국어 번역문만 출력하세요." to
            "다음을 한국어로 번역:\n\n$chunk"
        }
        return callAsync(webClient, model, listOf(
            VllmRequest.Message(role = "system", content = system),
            VllmRequest.Message(role = "user", content = user)
        ), maxTokens = 2500)
    }

    fun summarizeAsync(text: String): Mono<String> {
        val (baseUrl, model) = properties.resolve("DEFAULT")
        val system = """
You are an expert document analyst. Your task is to:
1. Identify the document type from the content.
2. Produce a structured summary in Korean using the most appropriate format for that document type.

Document type guidelines:
- Academic paper → use these sections: 연구 목적 / 핵심 도전 과제 / 핵심 이론 및 개념 / 제안 방법 / 실험 및 데이터 / 주요 결과 / 결론 및 시사점
- Business report / market analysis → 개요 / 핵심 현황 / 주요 발견 / 시사점 및 제언
- Legal / contract document → 문서 목적 / 주요 조항 / 당사자 의무 / 기한 및 조건 / 유의사항
- Technical manual / guide → 목적 및 대상 / 주요 기능 / 핵심 절차 / 주의사항
- Other → use the most suitable structure for the content

Rules:
- First line must be: **문서 유형: [detected type in Korean]**
- Use markdown ### headers appropriate for the document type.
- Write 3-5 specific sentences per section — be detailed and informative.
- Always extract and include: key theoretical terms/concepts, specific numbers/statistics, named methods or models, experimental conditions, and concrete findings.
- For academic papers: explicitly name core theoretical frameworks and concepts introduced. Include exact statistics from results.
- All output must be in Korean Hangul (한글) only. No Chinese characters, no Japanese kanji.
- All output must be in professional, natural Korean.
        """.trimIndent()

        val user = """
Analyze the following document, detect its type, and produce a detailed structured summary in Korean following the guidelines.

<document>
$text
</document>
        """.trimIndent()

        return callAsync(webClientFor(baseUrl), model, listOf(
            VllmRequest.Message(role = "system", content = system),
            VllmRequest.Message(role = "user", content = user)
        ), maxTokens = 1200)
    }

    fun summarize(text: String): String = summarizeAsync(text).block() ?: ""

    private fun callAsync(
        webClient: WebClient,
        model: String,
        messages: List<VllmRequest.Message>,
        maxTokens: Int = 1024
    ): Mono<String> {
        val request = VllmRequest(model = model, messages = messages, max_tokens = maxTokens)
        return webClient.post()
            .uri("/v1/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(VllmResponse::class.java)
            .map { sanitize(it.text()) }
    }

    private fun sanitize(text: String): String =
        text.filter { ch ->
            when {
                ch == '\n' || ch == '\r' || ch == '\t' -> true
                ch.code < 0x20 -> false
                ch.category == CharCategory.PRIVATE_USE -> false
                ch.category == CharCategory.SURROGATE -> false
                ch.category == CharCategory.FORMAT -> false
                else -> true
            }
        }
}