package com.insightai.pdfsummary.service

import com.insightai.pdfsummary.dto.PdfSummaryResponse
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

/**
 * 번역 결과를 한국어 PDF 파일로 내보내는 서비스.
 *
 * PDFBox를 사용하여 맑은 고딕(malgun.ttf) 폰트로 A4 PDF를 생성한다.
 * 요약 → 번역 전문 순서로 섹션을 구성하며, 페이지가 넘치면 자동으로 새 페이지를 추가한다.
 *
 * 폰트는 classpath `/fonts/malgun.ttf` 에서 로드하므로
 * `src/main/resources/fonts/malgun.ttf` 에 폰트 파일이 있어야 한다.
 */
@Service
class PdfExportService {

    private val margin = 50f
    private val pageWidth = PDRectangle.A4.width - 2 * margin
    private val pageHeight = PDRectangle.A4.height

    /**
     * 문서의 요약·번역 전문을 A4 PDF로 생성하여 바이트 배열로 반환한다.
     *
     * @param doc 요약·번역 결과가 담긴 DTO
     * @return 생성된 PDF 바이트 배열
     */
    fun export(doc: PdfSummaryResponse): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { pdDoc ->
            val fontStream = PdfExportService::class.java.getResourceAsStream("/fonts/malgun.ttf")
                ?: error("폰트 파일 없음: src/main/resources/fonts/malgun.ttf 에 malgun.ttf를 추가하세요")
            val font = PDType0Font.load(pdDoc, fontStream, true)

            val sections = buildList {
                add(16f to doc.fileName)
                add(0f to "")
                add(13f to "【요약】")
                add(0f to "")
                (doc.summary ?: "내용 없음").split("\n").forEach { add(10.5f to it) }
                add(0f to "")
                add(13f to "【번역 전문】")
                add(0f to "")
                (doc.translatedText ?: "내용 없음").split("\n").forEach { add(10.5f to it) }
            }

            var page = PDPage(PDRectangle.A4)
            pdDoc.addPage(page)
            var cs = PDPageContentStream(pdDoc, page)
            var y = pageHeight - margin

            for ((fontSize, text) in sections) {
                val leading = if (fontSize == 0f) 10f else fontSize * 1.6f
                val lines = if (fontSize == 0f) listOf("") else wrapText(text, font, fontSize.coerceAtLeast(10.5f))

                for (line in lines) {
                    if (y - leading < margin) {
                        cs.close()
                        page = PDPage(PDRectangle.A4)
                        pdDoc.addPage(page)
                        cs = PDPageContentStream(pdDoc, page)
                        y = pageHeight - margin
                    }
                    if (line.isNotEmpty()) {
                        cs.beginText()
                        cs.setFont(font, fontSize.coerceAtLeast(10.5f))
                        cs.newLineAtOffset(margin, y)
                        cs.showText(line)
                        cs.endText()
                    }
                    y -= leading
                }
            }
            cs.close()
            pdDoc.save(out)
        }
        return out.toByteArray()
    }

    private fun wrapText(text: String, font: PDType0Font, fontSize: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val safeText = text.filter { ch ->
            try { font.getStringWidth(ch.toString()); true } catch (e: Exception) { false }
        }
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in safeText) {
            current.append(ch)
            val width = try { font.getStringWidth(current.toString()) / 1000f * fontSize } catch (e: Exception) { 0f }
            if (width > pageWidth) {
                if (current.length > 1) {
                    result.add(current.dropLast(1).toString())
                    current = StringBuilder(ch.toString())
                }
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}