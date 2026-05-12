package com.insightai.pdfsummary

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PdfSummaryApplication

fun main(args: Array<String>) {
    runApplication<PdfSummaryApplication>(*args)
}
