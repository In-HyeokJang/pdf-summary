package com.insightai.pdfsummary.controller

import com.insightai.pdfsummary.domain.ProcessMode
import com.insightai.pdfsummary.dto.PdfSummaryResponse
import com.insightai.pdfsummary.dto.PdfUploadResponse
import com.insightai.pdfsummary.dto.TrainingRecord
import com.insightai.pdfsummary.service.PdfService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * PDF 처리 REST API 컨트롤러.
 *
 * Thymeleaf 폼과 외부 클라이언트가 공통으로 사용하는 JSON API를 제공한다.
 * 모든 엔드포인트는 [PdfService]에 처리를 위임하고 [ResponseEntity]로 감싸 반환한다.
 *
 * 엔드포인트 목록:
 * - `POST /api/pdf/upload` : PDF 업로드 및 처리 시작
 * - `POST /api/pdf/retry/{id}` : FAILED 문서 재처리
 * - `GET  /api/pdf/list` : 전체 문서 목록 (최신순)
 * - `GET  /api/pdf/{id}` : 단일 문서 상세 조회
 * - `GET  /api/pdf/training/export` : LoRA 파인튜닝 학습 데이터 JSON 내보내기
 */
@RestController
@RequestMapping("/api/pdf")
class PdfController(private val pdfService: PdfService) {

    /**
     * PDF 파일을 업로드하고 번역·요약 처리를 시작한다.
     *
     * @param file 업로드할 PDF 파일 (multipart)
     * @param sourceLang 원문 언어 코드 (EN / JA / ZH)
     * @param processMode 처리 모드 문자열 (TRANSLATE / SUMMARIZE / BOTH, 기본값: BOTH)
     */
    @PostMapping("/upload")
    fun upload(
        @RequestParam file: MultipartFile,
        @RequestParam sourceLang: String,
        @RequestParam(defaultValue = "BOTH") processMode: String
    ): ResponseEntity<PdfUploadResponse> =
        ResponseEntity.ok(pdfService.upload(file, sourceLang, runCatching { ProcessMode.valueOf(processMode) }.getOrDefault(ProcessMode.BOTH)))

    @PostMapping("/retry/{id}")
    fun retry(@PathVariable id: Long): ResponseEntity<PdfUploadResponse> {
        return try {
            ResponseEntity.ok(pdfService.retry(id))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/list")
    fun list(): ResponseEntity<List<PdfSummaryResponse>> =
        ResponseEntity.ok(pdfService.list())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<PdfSummaryResponse> =
        ResponseEntity.ok(pdfService.get(id))

    /**
     * Claude/Gemini로 생성된 DONE 문서를 LoRA 파인튜닝용 학습 데이터로 내보낸다.
     *
     * 반환 포맷: JSON 배열 (Alpaca 형식). GB10 파이썬 스크립트에서 직접 읽거나
     * `jq -c '.[]'` 로 JSONL 변환 가능.
     */
    @GetMapping("/training/export")
    fun exportTraining(): ResponseEntity<List<TrainingRecord>> =
        ResponseEntity.ok(pdfService.exportTrainingData())
}