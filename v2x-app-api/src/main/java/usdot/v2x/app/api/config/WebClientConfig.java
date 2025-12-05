package usdot.v2x.app.api.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for Spring WebClient setup.
 */
@Configuration
public class WebClientConfig {

    @Autowired
    private WebClientProperties webClientProperties;

    @PostConstruct
    public void init() {
        // Set static reference for backward compatibility with static getRetrySpec()
        // method
        WebClientConfig.staticWebClientProperties = webClientProperties;
    }

    private static WebClientProperties staticWebClientProperties;

    @Bean
    public WebClient.Builder webClientBuilder() {
        int connectTimeout = webClientProperties.getTimeout().getConnect();
        int readTimeout = webClientProperties.getTimeout().getRead();
        int writeTimeout = webClientProperties.getTimeout().getWrite();

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

    /**
     * Static method for backward compatibility.
     * Uses injected WebClientProperties if available, otherwise falls back to
     * defaults.
     */
    public static Retry getRetrySpec() {
        WebClientProperties props = staticWebClientProperties;
        if (props == null) {
            // Fallback to defaults if not yet initialized (shouldn't happen in normal
            // operation)
            props = new WebClientProperties();
        }
        return createRetrySpec(props);
    }

    private static Retry createRetrySpec(WebClientProperties props) {
        int maxAttempts = props.getRetry().getMaxAttempts();
        long initialDelay = props.getRetry().getBackoff().getInitialDelay();
        long maxDelay = props.getRetry().getBackoff().getMaxDelay();

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
