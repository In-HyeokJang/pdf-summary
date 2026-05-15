package com.insightai.pdfsummary.domain

enum class ProcessMode {
    TRANSLATE,   // 번역만
    SUMMARIZE,   // 요약만 (원문 직접 요약)
    BOTH         // 번역 + 요약
}