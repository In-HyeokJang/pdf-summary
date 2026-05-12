package com.insightai.pdfsummary.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class PdfParserService {

    fun extractText(file: MultipartFile): String {
        Loader.loadPDF(file.bytes).use { doc ->
            return PDFTextStripper().getText(doc)
                .replace('�', ' ')
                .trim()
        }
    }

    fun splitIntoChunks(text: String, maxChunkSize: Int = 2500): List<String> {
        val paragraphs = text.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (para in paragraphs) {
            if (current.isNotEmpty() && current.length + para.length + 2 > maxChunkSize) {
                chunks.add(current.toString().trim())
                current.clear()
            }
            if (para.length > maxChunkSize) {
                if (current.isNotEmpty()) { chunks.add(current.toString().trim()); current.clear() }
                var start = 0
                while (start < para.length) {
                    chunks.add(para.substring(start, minOf(start + maxChunkSize, para.length)))
                    start += maxChunkSize
                }
            } else {
                current.append(para).append("\n\n")
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks
    }
}