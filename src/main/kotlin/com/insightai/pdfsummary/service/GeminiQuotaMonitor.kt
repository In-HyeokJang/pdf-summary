package com.insightai.pdfsummary.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Gemini API 일일 할당량(RPD) 소진 상태를 추적하는 컴포넌트.
 *
 * 할당량 소진 시 1시간 동안 Gemini 호출을 차단하고 LOCAL(vLLM) fallback으로 전환한다.
 * Google은 매일 자정(태평양 시간) 할당량을 리셋하므로, 최대 24시간 내에 자동 복구된다.
 * 상태는 [GeminiService]가 기록하며, [PdfAsyncProcessor]가 읽어 라우팅 판단에 사용한다.
 */
@Component
class GeminiQuotaMonitor {
    private val log = LoggerFactory.getLogger(GeminiQuotaMonitor::class.java)

    @Volatile
    private var exhaustedUntil: Instant? = null

    /** 현재 Gemini 할당량이 소진 상태인지 반환한다. */
    fun isExhausted(): Boolean = exhaustedUntil?.let { Instant.now().isBefore(it) } ?: false

    /** 할당량 소진을 기록한다. 이후 1시간 동안 [isExhausted]가 true를 반환한다. */
    fun markExhausted() {
        exhaustedUntil = Instant.now().plus(1, ChronoUnit.HOURS)
        log.warn("[GeminiQuota] Quota 소진 감지 → 1시간 LOCAL fallback 적용 (until {})", exhaustedUntil)
    }
}

/** Gemini API 할당량 소진 시 발생하는 예외. PdfAsyncProcessor의 onErrorResume fallback 트리거로 사용된다. */
class GeminiQuotaExhaustedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)