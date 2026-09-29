package ru.practicum.stats.client;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import ru.practicum.stats.client.exception.StatsServerUnavailableException;
import ru.practicum.stats.dto.EndpointHitDto;
import ru.practicum.stats.dto.ViewStatsDto;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class StatsClient {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ParameterizedTypeReference<List<ViewStatsDto>> STATS_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;
    private final String statsServiceId;
    private final RetryTemplate retryTemplate;

    public StatsClient(DiscoveryClient discoveryClient, String statsServiceId, RetryTemplate retryTemplate) {
        this.restClient = RestClient.create();
        this.discoveryClient = discoveryClient;
        this.statsServiceId = statsServiceId;
        this.retryTemplate = retryTemplate;
    }

    public void hit(EndpointHitDto hit) {
        restClient.post()
                .uri(makeUri("/hit"))
                .body(hit)
                .retrieve()
                .toBodilessEntity();
    }

    public List<ViewStatsDto> getStats(LocalDateTime start,
                                       LocalDateTime end,
                                       List<String> uris,
                                       boolean unique) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(makeUri("/stats"))
                .queryParam("start", start.format(FORMATTER))
                .queryParam("end", end.format(FORMATTER))
                .queryParam("unique", unique);

        if (uris != null && !uris.isEmpty()) {
            uriBuilder.queryParam("uris", uris.toArray());
        }

        List<ViewStatsDto> body = restClient.get()
                .uri(uriBuilder.build().encode().toUri())
                .retrieve()
                .body(STATS_LIST);

        return body == null ? Collections.emptyList() : body;
    }

    private URI makeUri(String path) {
        ServiceInstance instance = retryTemplate.execute(context -> getInstance());
        return instance.getUri().resolve(path);
    }

    private ServiceInstance getInstance() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(statsServiceId);
            if (instances.isEmpty()) {
                throw new StatsServerUnavailableException(
                        "No instances registered for statistics service: " + statsServiceId
                );
            }
            return instances.getFirst();
        } catch (StatsServerUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new StatsServerUnavailableException(
                    "Failed to discover statistics service: " + statsServiceId,
                    exception
            );
        }
    }
}
