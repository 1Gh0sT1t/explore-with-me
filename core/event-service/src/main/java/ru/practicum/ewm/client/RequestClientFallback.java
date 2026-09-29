package ru.practicum.ewm.client;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RequestClientFallback implements RequestClient {

    @Override
    public Map<Long, Long> getConfirmedRequests(List<Long> eventIds) {
        return eventIds.stream().collect(Collectors.toMap(Function.identity(), ignored -> 0L));
    }
}
