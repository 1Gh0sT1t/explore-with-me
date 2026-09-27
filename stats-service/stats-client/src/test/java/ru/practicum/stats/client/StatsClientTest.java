package ru.practicum.stats.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.retry.support.RetryTemplate;
import ru.practicum.stats.client.exception.StatsServerUnavailableException;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.dto.ViewStatsDto;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsClientTest {

    private static final String SERVICE_ID = "stats-server";

    @Mock
    private DiscoveryClient discoveryClient;

    private HttpServer server;
    private StatsClient statsClient;
    private AtomicReference<String> statsQuery;

    @BeforeEach
    void setUp() throws IOException {
        statsQuery = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hit", exchange -> respond(exchange, 201, ""));
        server.createContext("/stats", exchange -> {
            statsQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "[{\"app\":\"main-service\",\"uri\":\"/events/1\",\"hits\":2}]");
        });
        server.start();

        DefaultServiceInstance instance = new DefaultServiceInstance(
                "stats-1",
                SERVICE_ID,
                "localhost",
                server.getAddress().getPort(),
                false
        );
        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(List.of(instance));
        statsClient = new StatsClient(discoveryClient, SERVICE_ID, RetryTemplate.builder().maxAttempts(1).build());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void hitShouldUseDiscoveredInstance() {
        EndpointHitDto hit = new EndpointHitDto(
                "main-service",
                "/events/1",
                "127.0.0.1",
                LocalDateTime.of(2026, 9, 27, 12, 0)
        );

        statsClient.hit(hit);
    }

    @Test
    void getStatsShouldSendEncodedQueryAndReadResponse() {
        List<ViewStatsDto> result = statsClient.getStats(
                LocalDateTime.of(2026, 9, 1, 10, 0),
                LocalDateTime.of(2026, 9, 27, 10, 0),
                List.of("/events/1"),
                true
        );

        assertThat(result).containsExactly(new ViewStatsDto("main-service", "/events/1", 2L));
        assertThat(statsQuery.get())
                .contains("start=2026-09-01%2010:00:00")
                .contains("end=2026-09-27%2010:00:00")
                .contains("uris=/events/1")
                .contains("unique=true");
    }

    @Test
    void shouldFailWhenServiceIsNotRegistered() {
        when(discoveryClient.getInstances(SERVICE_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> statsClient.hit(new EndpointHitDto(
                "main-service",
                "/events/1",
                "127.0.0.1",
                LocalDateTime.now()
        )))
                .isInstanceOf(StatsServerUnavailableException.class)
                .hasMessageContaining(SERVICE_ID);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
