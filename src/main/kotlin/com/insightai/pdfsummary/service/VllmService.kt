package com.insightai.pdfsummary.service

import com.insightai.pdfsummary.dto.VllmRequest
import com.insightai.pdfsummary.dto.VllmResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Service
class VllmService(
    private val vllmWebClient: WebClient,
    @Value("\${vllm.model}") private val model: String
) {

    fun translateAsync(chunk: String, sourceLang: String): Mono<String> {
        return callAsync(
            messages = listOf(
                VllmRequest.Message(
                    role = "system",
                    content = """You are a professional academic translator. Translate ALL text into Korean.

Rules:
- Translate body text, titles, and section headings into natural Korean.
- Author names: transliterate every author name phonetically into Korean based on its language of origin. Apply this consistently throughout the entire text, including inside parenthetical in-text citations like (Smith & Jones, 2020).
- Publisher names: transliterate phonetically, do NOT translate their meaning (e.g. a publisher named after a word should still be transliterated, not translated).
- Journal/book titles: translate the meaning into natural Korean.
- Never leave any word in the original language. Never output '?' as a placeholder.
- Output only the Korean translation, nothing else."""
                ),
                VllmRequest.Message(
                    role = "user",
                    content = "Translate the following $sourceLang text into Korean:\n\n$chunk"
                )
            ),
            maxTokens = 1500
        )
    }

    fun summarizeAsync(text: String): Mono<String> {
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
- For academic papers: explicitly name core theoretical frameworks and concepts introduced (e.g., a specific rule, model, or effect). Include exact statistics from results.
- All output must be in professional, natural Korean.
        """.trimIndent()

        val user = """
Analyze the following document, detect its type, and produce a detailed structured summary in Korean following the guidelines.

<document>
$text
</document>
        """.trimIndent()

        return callAsync(
            messages = listOf(
                VllmRequest.Message(role = "system", content = system),
                VllmRequest.Message(role = "user", content = user)
            ),
            maxTokens = 1024
        )
    }

    fun summarize(text: String): String = summarizeAsync(text).block() ?: ""

    private fun callAsync(
        messages: List<VllmRequest.Message>,
        maxTokens: Int = 1024
    ): Mono<String> {
        val request = VllmRequest(
            model = model,
            messages = messages,
            max_tokens = maxTokens
        )
        return vllmWebClient.post()
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
                ch.code < 0x20 -> false                                      // control chars
                ch == '�' -> false                                      // replacement char
                ch.category == CharCategory.PRIVATE_USE -> false
                ch.category == CharCategory.SURROGATE -> false
                ch.category == CharCategory.FORMAT -> false
                ch.code in 0x4E00..0x9FFF -> false                          // CJK Unified Ideographs
                ch.code in 0x3400..0x4DBF -> false                          // CJK Extension A
                ch.code in 0x20000..0x2A6DF -> false                        // CJK Extension B
                ch.code in 0xF900..0xFAFF -> false                          // CJK Compatibility Ideographs
                else -> true
            }
        }
}