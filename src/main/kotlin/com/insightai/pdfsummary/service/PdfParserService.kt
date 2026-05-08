package com.insightai.pdfsummary.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class PdfParserService {

    fun extractText(file: MultipartFile): String {
        Loader.loadPDF(file.bytes).use { doc ->
            return PDFTextStripper().getText(doc).trim()
        }
    }

    fun splitIntoChunks(text: String, chunkSize: Int = 2500): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            chunks.add(text.substring(start, minOf(start + chunkSize, text.length)))
            start += chunkSize
        }
        return chunks
    }
}