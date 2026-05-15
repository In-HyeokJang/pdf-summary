package com.insightai.pdfsummary.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * PDFBox를 사용한 텍스트 추출 및 청크 분할 서비스.
 *
 * 추출된 텍스트는 번역·요약 파이프라인에 직접 전달되므로
 * 인코딩 오류 문자(`�`) 제거와 문단 경계 기반 분할이 품질에 중요하다.
 */
@Service
class PdfParserService {

    /**
     * PDF 파일에서 텍스트를 추출한다.
     *
     * PDFBox [PDFTextStripper]로 전체 페이지 텍스트를 읽고,
     * 디코딩 실패 문자(`�`)를 공백으로 치환하여 vLLM 토크나이저 오류를 방지한다.
     *
     * @param file 업로드된 PDF 파일
     * @return 추출된 원문 텍스트 (양 끝 공백 제거됨)
     */
    fun extractText(file: MultipartFile): String {
        Loader.loadPDF(file.bytes).use { doc ->
            return PDFTextStripper().getText(doc)
                .replace('�', ' ')
                .trim()
        }
    }

    /**
     * 텍스트를 문단 경계 기준으로 청크로 분할한다.
     *
     * 문단(`\n{2,}`) 단위로 분리한 뒤 [maxChunkSize]를 초과할 때 새 청크를 시작한다.
     * 청크 간 [overlapSize]만큼 앞 청크 끝 내용을 겹쳐서 문맥 단절을 줄인다.
     * 단일 문단이 [maxChunkSize]를 초과하면 강제로 잘라 처리한다.
     *
     * @param text 분할할 원문 텍스트
     * @param maxChunkSize 청크 최대 문자 수 (기본 2500 — 번역용, 요약 단계는 5000 사용)
     * @param overlapSize 청크 간 겹침 문자 수 (기본 300)
     * @return 분할된 청크 리스트
     */
    fun splitIntoChunks(text: String, maxChunkSize: Int = 2500, overlapSize: Int = 300): List<String> {
        val paragraphs = text.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (para in paragraphs) {
            if (current.isNotEmpty() && current.length + para.length + 2 > maxChunkSize) {
                val chunkText = current.toString().trim()
                chunks.add(chunkText)
                current.clear()
                val overlap = chunkText.takeLast(overlapSize)
                current.append(overlap).append("\n\n")
            }
            if (para.length > maxChunkSize) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                var start = 0
                while (start < para.length) {
                    val end = minOf(start + maxChunkSize, para.length)
                    chunks.add(para.substring(start, end))
                    start += maxChunkSize - overlapSize
                }
                val lastLongChunk = para.takeLast(overlapSize)
                current.append(lastLongChunk).append("\n\n")
            } else {
                current.append(para).append("\n\n")
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks
    }
}