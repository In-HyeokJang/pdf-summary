package com.insightai.pdfsummary.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class WebClientConfig(
    @Value("\${vllm.base-url}") private val vllmBaseUrl: String
) {

    @Bean
    fun vllmWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(600))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(600, TimeUnit.SECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(600, TimeUnit.SECONDS))
            }

        return WebClient.builder()
            .baseUrl(vllmBaseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
            .build()
    }
}