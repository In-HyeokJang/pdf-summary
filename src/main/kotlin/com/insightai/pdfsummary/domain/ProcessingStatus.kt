package com.insightai.pdfsummary.domain

enum class ProcessingStatus(val displayName: String) {
    PENDING("대기"),
    PROCESSING("처리중"),
    DONE("완료"),
    FAILED("실패"),
    CACHED("캐시")
}