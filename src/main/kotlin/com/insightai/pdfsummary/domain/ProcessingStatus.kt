package com.insightai.pdfsummary.domain

/**
 * PDF 문서의 처리 상태를 나타내는 열거형.
 *
 * 상태 전이 흐름:
 * PENDING → PROCESSING → DONE
 *                      ↘ FAILED  (예외 발생 또는 30분 스테일 타임아웃)
 * (중복 업로드 시) → CACHED
 *
 * @property displayName UI 목록·상세 페이지에 표시할 한국어 레이블
 */
enum class ProcessingStatus(val displayName: String) {
    /** 업로드 직후 초기 상태. 정상 흐름에서는 즉시 PROCESSING으로 전환된다. */
    PENDING("대기"),

    /** 번역·요약이 백그라운드 스레드(pdfTaskExecutor)에서 진행 중인 상태. */
    PROCESSING("처리중"),

    /** 번역·요약이 성공적으로 완료된 상태. */
    DONE("완료"),

    /**
     * 처리 실패 상태.
     * 두 가지 경로로 진입한다:
     * 1. PdfAsyncProcessor 에서 예외 발생 시 catch 블록이 즉시 전환
     * 2. PdfService.recoverStaleProcessing() 스케줄러가 PROCESSING 상태로
     *    30분 이상 경과한 문서를 자동으로 전환 (매 1분마다 검사)
     */
    FAILED("실패"),

    /**
     * 동일 파일(SHA-256 해시) + 동일 processMode 조합이 이미 DONE 상태로 존재할 때
     * 중복 업로드 요청에 대해 즉시 반환되는 상태.
     * 실제 처리는 수행되지 않으며 기존 문서의 결과를 재사용한다.
     */
    CACHED("캐시")
}