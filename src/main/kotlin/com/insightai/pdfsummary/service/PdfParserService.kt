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