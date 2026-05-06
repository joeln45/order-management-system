package com.joel.ordermanagement.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Shared WebClient bean for outbound HTTP. Right now it only carries the
 * wholesaler traffic, but future integrations can reuse the same instance.
 *
 * Two timeout knobs are wired in: a connect timeout for the TCP handshake,
 * and a response/read/write timeout for waiting on bytes once the
 * connection is open. Retry policy is applied per-call by WholesalerClient
 * rather than here, so different endpoints can pick different rules.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(
            @Value("${wholesaler.base-url}") String baseUrl,
            @Value("${wholesaler.connect-timeout}") Duration connectTimeout,
            @Value("${wholesaler.response-timeout}") Duration responseTimeout) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .responseTimeout(responseTimeout)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(responseTimeout.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(responseTimeout.toSeconds(), TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
