package com.insightai.pdfsummary.controller

import com.insightai.pdfsummary.dto.PdfSummaryResponse
import com.insightai.pdfsummary.dto.PdfUploadResponse
import com.insightai.pdfsummary.service.PdfService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/pdf")
class PdfController(private val pdfService: PdfService) {

    @PostMapping("/upload")
    fun upload(
        @RequestParam file: MultipartFile,
        @RequestParam sourceLang: String
    ): ResponseEntity<PdfUploadResponse> =
        ResponseEntity.ok(pdfService.upload(file, sourceLang))

    @GetMapping("/list")
    fun list(): ResponseEntity<List<PdfSummaryResponse>> =
        ResponseEntity.ok(pdfService.list())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<PdfSummaryResponse> =
        ResponseEntity.ok(pdfService.get(id))
}