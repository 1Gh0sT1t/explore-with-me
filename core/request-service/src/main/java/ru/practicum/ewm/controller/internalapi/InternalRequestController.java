package ru.practicum.ewm.controller.internalapi;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.ewm.model.RequestStatus;
import ru.practicum.ewm.repository.RequestRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/requests")
public class InternalRequestController {

    private final RequestRepository requestRepository;

    @GetMapping("/users/{userId}/events/{eventId}/confirmed")
    public boolean hasConfirmedRequest(@PathVariable Long userId, @PathVariable Long eventId) {
        return requestRepository.existsByEventIdAndRequesterIdAndStatus(eventId, userId, RequestStatus.CONFIRMED);
    }

    @PostMapping("/counts")
    public Map<Long, Long> getConfirmedRequests(@RequestBody List<Long> eventIds) {
        return requestRepository.countByEventIdsAndStatus(eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
