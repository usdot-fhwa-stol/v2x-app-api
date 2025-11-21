package usdot.v2x.app.api.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for Spring WebClient setup.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        int connectTimeout = Integer.parseInt(System.getenv().getOrDefault("WEB_CLIENT_CONNECT_TIMEOUT", "10000"));
        int readTimeout = Integer.parseInt(System.getenv().getOrDefault("WEB_CLIENT_READ_TIMEOUT", "30000"));
        int writeTimeout = Integer.parseInt(System.getenv().getOrDefault("WEB_CLIENT_WRITE_TIMEOUT", "10000"));

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(readTimeout))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(readTimeout / 1000, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout / 1000, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(loggingFilter());
    }

    private ExchangeFilterFunction loggingFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            return Mono.just(clientRequest);
        });
    }

    public static Retry getRetrySpec() {
        int maxAttempts = Integer.parseInt(System.getenv().getOrDefault("WEB_CLIENT_RETRY_MAX_ATTEMPTS", "3"));
        long initialDelay = Long.parseLong(System.getenv().getOrDefault("WEB_CLIENT_RETRY_INITIAL_DELAY", "1000"));
        long maxDelay = Long.parseLong(System.getenv().getOrDefault("WEB_CLIENT_RETRY_MAX_DELAY", "5000"));

        return Retry.backoff(maxAttempts, Duration.ofMillis(initialDelay))
                .maxBackoff(Duration.ofMillis(maxDelay))
                .jitter(0.5)
                .filter(throwable -> {
                    // Retry on network-related exceptions
                    return throwable instanceof java.net.UnknownHostException ||
                            throwable instanceof java.net.ConnectException ||
                            throwable instanceof java.net.SocketTimeoutException ||
                            throwable instanceof org.springframework.web.reactive.function.client.WebClientRequestException;
                });
    }
}
