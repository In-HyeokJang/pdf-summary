package com.insightai.pdfsummary.dto

/**
 * LoRA 파인튜닝용 학습 데이터 레코드 (Alpaca 포맷).
 *
 * /api/pdf/training/export 엔드포인트에서 JSONL로 직렬화된다.
 * GB10의 파이썬 파인튜닝 스크립트가 이 포맷을 읽어 학습 데이터로 사용한다.
 *
 * @property id 원본 PdfDocument ID (학습 데이터 추적용)
 * @property task 작업 유형 (translate / summarize)
 * @property lang 원문 언어 코드 (EN / JA / ZH)
 * @property instruction 작업 지시문
 * @property input 원문 텍스트
 * @property output 목표 출력 (번역문 또는 요약문)
 * @property provider 생성에 사용한 LLM 제공자 (CLAUDE / GEMINI)
 */
data class TrainingRecord(
    val id: Long,
    val task: String,
    val lang: String,
    val instruction: String,
    val input: String,
    val output: String,
    val provider: String
)