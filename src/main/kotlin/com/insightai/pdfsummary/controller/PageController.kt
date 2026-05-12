package com.insightai.pdfsummary.controller

import com.insightai.pdfsummary.service.PdfExportService
import com.insightai.pdfsummary.service.PdfService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class PageController(
    private val pdfService: PdfService,
    private val pdfExportService: PdfExportService
) {

    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("documents", pdfService.list())
        return "index"
    }

    @PostMapping("/upload")
    fun upload(
        @RequestParam file: MultipartFile,
        @RequestParam sourceLang: String,
        redirectAttributes: RedirectAttributes
    ): String {
        val result = pdfService.upload(file, sourceLang)
        redirectAttributes.addFlashAttribute("message", "업로드 완료: ${result.fileName}")
        return "redirect:/"
    }

    @GetMapping("/detail/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        model.addAttribute("doc", pdfService.get(id))
        return "detail"
    }

    @GetMapping("/download/{id}")
    fun download(@PathVariable id: Long, response: HttpServletResponse) {
        val doc = pdfService.get(id)
        val pdfBytes = pdfExportService.export(doc)
        val filename = doc.fileName.removeSuffix(".pdf") + "_번역.pdf"
        response.contentType = "application/pdf"
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''${java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20")}")
        response.outputStream.write(pdfBytes)
    }
}