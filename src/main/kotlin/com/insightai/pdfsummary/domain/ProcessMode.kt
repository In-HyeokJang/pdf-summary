package com.insightai.pdfsummary.domain

/**
 * 사용자가 업로드 시 선택하는 PDF 처리 모드.
 *
 * 각 모드는 PdfAsyncProcessor 에서 독립적인 처리 경로로 분기된다.
 * SUMMARIZE·BOTH 모드에서 동일 파일의 번역본이 이미 존재하면
 * 번역 단계를 건너뛰고 기존 번역본을 재사용(fast-track)한다.
 *
 * @property displayName UI 버튼·목록·상세 페이지에 표시할 한국어 레이블
 */
enum class ProcessMode(val displayName: String) {
    /** 원문을 한국어로 번역만 수행. 요약은 생성하지 않는다. */
    TRANSLATE("번역"),

    /**
     * 요약만 수행.
     * 동일 파일의 번역본이 이미 있으면 그 한국어 텍스트로 소요약(fast-track),
     * 없으면 원문 언어 그대로 소요약(summarizeFromSourceAsync)한 뒤 최종 요약한다.
     */
    SUMMARIZE("요약"),

    /**
     * 번역 후 요약까지 순차 수행.
     * 동일 파일의 번역본이 이미 있으면 번역 단계를 건너뛴다(fast-track).
     */
    BOTH("번역/요약")
}