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
        webClient: WebClient, model: String, chunk: String,
        sourceLang: String, retry: Boolean
    ): Mono<String> {
        val (system, user) = if (!retry) {
            ("""
You are a professional Korean translator specializing in academic papers, patents, contracts, and technical documents.

Translation rules:
- Output ONLY the Korean translation. No meta-comments, no explanations.
- Tone: formal declarative style (한국어 ~다/~이다 체). Never mix 존댓말 and 반말.
- Technical terms: on first appearance include English in parentheses — e.g., 강화학습(Reinforcement Learning). Subsequent occurrences: Korean only.
- Do NOT translate: mathematical expressions, units, model names, dataset names, metric names (e.g. BLEU, COMET, F1, Llama, MNIST).
- Tables: preserve as markdown table with Korean headers.
- Figure/Table captions: "Figure 1." → "그림 1.", "Table 2." → "표 2."
- If the chunk begins or ends mid-sentence, translate naturally — do not add or remove content to fill the gap.
- No Chinese characters (漢字) or Japanese kana in output.""".trimIndent()) to
            "Translate the following $sourceLang text into Korean:\n\n$chunk"
        } else {
            ("""
전문 한국어 번역가입니다. 학술 논문·특허·계약서·기술문서를 전문으로 합니다.

번역 규칙:
- 한국어 번역문만 출력하세요. 설명이나 메타 코멘트 금지.
- 문체: ~다/~이다 체 통일. 존댓말·반말 혼용 금지.
- 전문용어 첫 등장 시 영문 병기: 예) 강화학습(Reinforcement Learning).
- 수식·단위·모델명·데이터셋명·지표명은 원문 유지.
- 한자(漢字)·일본어 가나 출력 금지.""".trimIndent()) to
            "다음을 한국어로 번역:\n\n$chunk"
        }
        return callAsync(webClient, model, listOf(
            VllmRequest.Message(role = "system", content = system),
            VllmRequest.Message(role = "user", content = user)
        ), maxTokens = 2500)
    }

    fun chunkSummarizeAsync(chunk: String): Mono<String> {
        val (baseUrl, model) = properties.resolve("DEFAULT")
        val system = """
다음 텍스트의 핵심 내용을 한국어로 요약하세요.

출력 형식 (3문장 고정):
1문장: 이 단락의 핵심 주장 1개 (수치·고유명사·모델명 포함)
2문장: 핵심 주장을 뒷받침하는 근거 또는 방법
3문장: 추가 근거 또는 결과 수치

규칙:
- 수치·통계·고유명사·모델명·데이터셋명은 반드시 포함하세요.
- 불필요한 접속어·부연 설명 제거.
- 한국어 ~다/~이다 체 통일. 한자·가나 출력 금지.
- 3문장을 초과하지 마세요.
        """.trimIndent()
        return callAsync(webClientFor(baseUrl), model, listOf(
            VllmRequest.Message(role = "system", content = system),
            VllmRequest.Message(role = "user", content = chunk)
        ), maxTokens = 500)
    }

    fun summarizeAsync(text: String): Mono<String> {
        val (baseUrl, model) = properties.resolve("DEFAULT")
        val system = """
당신은 전문 문서 분석가입니다. 주어진 문서의 유형을 판별하고 해당 유형에 최적화된 한국어 구조화 요약을 작성하세요.

[문서 유형별 섹션 구조]

학술 논문(Academic Paper):
### 연구 목적
### 핵심 도전 과제
### 핵심 이론 및 개념
### 제안 방법
### 실험 및 데이터
### 주요 결과
### 결론 및 시사점
→ 주요 결과 섹션에 수치(정확도·F1·BLEU 등) 최소 3개 이상 명시할 것.

특허(Patent):
### 발명의 목적
### 핵심 청구항
### 기술적 특징 및 구성
### 적용 분야 및 효과

계약서(Contract):
### 계약 목적 및 당사자
### 핵심 조항
### 당사자 의무사항
### 기한 및 조건
### 유의사항

기술문서(Technical Document):
### 목적 및 대상
### 주요 기능
### 핵심 절차
### 주의사항

보고서/분석서(Report):
### 개요
### 핵심 현황
### 주요 발견
### 시사점 및 제언

기타: 내용에 가장 적합한 구조 사용.

[공통 규칙]
- 첫 줄 반드시: **문서 유형: [판별된 유형]**
- 섹션 헤더는 ### 사용. bullet point보다 ### 구조 우선.
- 각 섹션 3~5문장. 구체적 수치·고유명사·모델명 포함.
- 전문용어 첫 등장 시 영문 병기: 예) 자연어처리(Natural Language Processing).
- 문체: ~다/~이다 체 통일. 한자·가나 출력 금지.
- 불필요한 서론·결어 없이 바로 **문서 유형:** 으로 시작.
        """.trimIndent()

        val user = """
다음 문서를 분석하여 유형을 판별하고, 해당 유형의 구조에 맞춰 상세한 한국어 요약을 작성하세요.

<document>
$text
</document>
        """.trimIndent()

        log.info("[TIMING] 요약 요청 → 모델: {} ({}자)", model, text.length)
        return callAsync(webClientFor(baseUrl), model, listOf(
            VllmRequest.Message(role = "system", content = system),
            VllmRequest.Message(role = "user", content = user)
        ), maxTokens = 1500)
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