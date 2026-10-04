package ru.practicum.ewm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient(name = "request-service", fallback = RequestClientFallback.class)
public interface RequestClient {

    @PostMapping("/internal/requests/counts")
    Map<Long, Long> getConfirmedRequests(@RequestBody List<Long> eventIds);

    @GetMapping("/internal/requests/users/{userId}/events/{eventId}/confirmed")
    boolean hasConfirmedRequest(@PathVariable Long userId, @PathVariable Long eventId);
}
