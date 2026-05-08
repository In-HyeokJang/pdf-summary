package com.insightai.pdfsummary

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PdfSummaryApplication

fun main(args: Array<String>) {
    runApplication<PdfSummaryApplication>(*args)
}
