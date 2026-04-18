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
 * A single shared {@link WebClient} bean for all outbound HTTP — currently
 * just the wholesaler, but set up so the same instance can be reused when
 * we add more integrations later.
 * <p>
 * Timeouts are explicit at two levels:
 * <ul>
 *   <li><b>Connect</b> — how long Netty will wait for the TCP handshake.</li>
 *   <li><b>Read/Write/Response</b> — how long it will wait for bytes after
 *       the connection is open.</li>
 * </ul>
 * Retries are applied per-call by {@link com.joel.ordermanagement.wholesaler.WholesalerClient}
 * so different endpoints can use different policies.
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
