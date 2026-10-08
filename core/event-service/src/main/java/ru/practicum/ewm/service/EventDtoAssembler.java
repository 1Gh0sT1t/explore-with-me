package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.client.CommentClient;
import ru.practicum.ewm.client.RequestClient;
import ru.practicum.ewm.dto.EventFullDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.UserDto;
import ru.practicum.ewm.dto.UserShortDto;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.Event;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EventDtoAssembler {

    private final EventMapper eventMapper;
    private final RemoteUserService remoteUserService;
    private final RequestClient requestClient;
    private final CommentClient commentClient;
    private final StatsHelperService statsHelperService;

    public List<EventShortDto> toShortDtos(List<Event> events) {
        return toShortDtos(events, null);
    }

    public List<EventShortDto> toShortDtos(List<Event> events, Map<Long, Long> confirmedRequests) {
        Context context = loadContext(events, confirmedRequests);
        return events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toShortDto(event);
                    context.fill(dto, event);
                    return dto;
                })
                .toList();
    }

    public List<EventFullDto> toFullDtos(List<Event> events) {
        Context context = loadContext(events, null);
        return events.stream()
                .map(event -> {
                    EventFullDto dto = eventMapper.toFullDto(event);
                    context.fill(dto, event);
                    return dto;
                })
                .toList();
    }

    public EventShortDto toShortDto(Event event) {
        return toShortDtos(List.of(event)).getFirst();
    }

    public EventFullDto toFullDto(Event event) {
        return toFullDtos(List.of(event)).getFirst();
    }

    public Map<Long, Long> getConfirmedRequests(List<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        return requestClient.getConfirmedRequests(eventIds(events));
    }

    private Context loadContext(List<Event> events, Map<Long, Long> confirmedRequests) {
        if (events.isEmpty()) {
            return new Context(Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<Long> eventIds = eventIds(events);
        List<Long> userIds = events.stream().map(Event::getInitiatorId).distinct().toList();
        return new Context(
                remoteUserService.getUsers(userIds),
                confirmedRequests == null ? requestClient.getConfirmedRequests(eventIds) : confirmedRequests,
                commentClient.getPublishedCommentCounts(eventIds),
                statsHelperService.getRatings(events)
        );
    }

    private List<Long> eventIds(List<Event> events) {
        return events.stream().map(Event::getId).toList();
    }

    private static UserShortDto user(Event event, Map<Long, UserDto> users) {
        UserDto user = users.get(event.getInitiatorId());
        return user == null
                ? new UserShortDto(event.getInitiatorId(), "")
                : new UserShortDto(user.getId(), user.getName());
    }

    private record Context(
            Map<Long, UserDto> users,
            Map<Long, Long> requests,
            Map<Long, Long> comments,
            Map<Long, Double> ratings
    ) {
        private void fill(EventShortDto dto, Event event) {
            dto.setInitiator(user(event, users));
            dto.setConfirmedRequests(requests.getOrDefault(event.getId(), 0L));
            dto.setComments(comments.getOrDefault(event.getId(), 0L));
            dto.setRating(ratings.getOrDefault(event.getId(), 0.0));
        }

        private void fill(EventFullDto dto, Event event) {
            dto.setInitiator(user(event, users));
            dto.setConfirmedRequests(requests.getOrDefault(event.getId(), 0L));
            dto.setComments(comments.getOrDefault(event.getId(), 0L));
            dto.setRating(ratings.getOrDefault(event.getId(), 0.0));
        }
    }
}
