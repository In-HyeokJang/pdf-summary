package com.insightai.pdfsummary.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * vLLM API 호출에 사용하는 [WebClient] 팩토리.
 *
 * 32B 모델은 응답에 수 분이 걸리므로 타임아웃을 600초로 설정한다.
 * 각 vLLM 엔드포인트마다 별도 인스턴스가 필요해 빈으로 등록하지 않고 팩토리 메서드로 구성한다.
 * [VllmService]가 baseUrl별로 캐싱하여 반복 생성을 방지한다.
 */
@Configuration
class WebClientConfig {

    /**
     * 지정된 baseUrl을 기준으로 [WebClient]를 생성한다.
     *
     * - 연결 타임아웃: 10초
     * - 응답/읽기/쓰기 타임아웃: 600초 (32B 모델 대응)
     * - 응답 버퍼: 10MB (대용량 번역 응답 처리)
     *
     * @param baseUrl vLLM 서버 주소 (예: `http://x.x.x.x:8000`)
     */
    fun buildWebClient(baseUrl: String): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(600))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(600, TimeUnit.SECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(600, TimeUnit.SECONDS))
            }
        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
            .build()
    }
}