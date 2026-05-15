package com.insightai.pdfsummary.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * PDF 비동기 처리를 위한 스레드 풀 설정.
 *
 * `@Async("pdfTaskExecutor")`로 지정된 메서드([PdfAsyncProcessor.processAsync])가
 * 이 풀에서 실행된다. 번역+요약은 I/O 바운드이므로 코어 수보다 작게 설정해도 충분하고,
 * 큐를 두어 동시 업로드가 몰려도 요청을 잃지 않도록 한다.
 *
 * 풀 설정 근거:
 * - corePoolSize=2 : 평상시 2개 문서 병행 처리
 * - maxPoolSize=4  : 부하 급등 시 최대 4개
 * - queueCapacity=20 : 큐가 꽉 차면 TaskRejectedException → 업로더에게 즉시 오류 반환
 * - awaitTermination=600s : 가장 오래 걸리는 번역+요약(~10분)을 종료 전 완료 대기
 */
@Configuration
class AsyncConfig {
    /**
     * `pdfTaskExecutor` 이름으로 등록되는 스레드 풀 빈.
     * `@Async("pdfTaskExecutor")` 어노테이션에서 이 이름으로 참조된다.
     */
    @Bean("pdfTaskExecutor")
    fun pdfTaskExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        queueCapacity = 20
        setThreadNamePrefix("pdf-async-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(600) // 10분: 대부분의 번역 완료 시간 커버
        initialize()
    }
}