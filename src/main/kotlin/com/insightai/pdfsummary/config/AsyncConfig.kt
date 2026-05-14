package com.insightai.pdfsummary.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
class AsyncConfig {
    @Bean("pdfTaskExecutor")
    fun pdfTaskExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        queueCapacity = 20
        setThreadNamePrefix("pdf-async-")
        initialize()
    }
}