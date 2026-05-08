package com.insightai.pdfsummary.service

import com.insightai.pdfsummary.dto.PdfSummaryResponse
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File

@Service
class PdfExportService {

    private val fontPath = "C:/Windows/Fonts/malgun.ttf"
    private val margin = 50f
    private val pageWidth = PDRectangle.A4.width - 2 * margin
    private val pageHeight = PDRectangle.A4.height

    fun export(doc: PdfSummaryResponse): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { pdDoc ->
            val font = PDType0Font.load(pdDoc, File(fontPath))

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