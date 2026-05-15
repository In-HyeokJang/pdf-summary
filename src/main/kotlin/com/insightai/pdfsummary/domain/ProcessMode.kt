package com.insightai.pdfsummary.domain

enum class ProcessMode(val displayName: String) {
    TRANSLATE("번역"),
    SUMMARIZE("요약"),
    BOTH("번역/요약")
}