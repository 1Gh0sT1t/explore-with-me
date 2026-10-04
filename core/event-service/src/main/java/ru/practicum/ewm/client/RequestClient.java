package ru.practicum.ewm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "request-service", fallback = RequestClientFallback.class)
public interface RequestClient {

    @PostMapping("/internal/requests/counts")
    Map<Long, Long> getConfirmedRequests(@RequestBody List<Long> eventIds);
}
