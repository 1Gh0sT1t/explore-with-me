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
        Context context = loadContext(events);
        return events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toShortDto(event);
                    fill(dto, event, context);
                    return dto;
                })
                .toList();
    }

    public List<EventFullDto> toFullDtos(List<Event> events) {
        Context context = loadContext(events);
        return events.stream()
                .map(event -> {
                    EventFullDto dto = eventMapper.toFullDto(event);
                    fill(dto, event, context);
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

    private Context loadContext(List<Event> events) {
        if (events.isEmpty()) {
            return new Context(Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<Long> eventIds = eventIds(events);
        List<Long> userIds = events.stream().map(Event::getInitiatorId).distinct().toList();
        return new Context(
                remoteUserService.getUsers(userIds),
                requestClient.getConfirmedRequests(eventIds),
                commentClient.getPublishedCommentCounts(eventIds),
                statsHelperService.getViews(events)
        );
    }

    private List<Long> eventIds(List<Event> events) {
        return events.stream().map(Event::getId).toList();
    }

    private void fill(EventShortDto dto, Event event, Context context) {
        dto.setInitiator(user(event, context.users()));
        dto.setConfirmedRequests(context.requests().getOrDefault(event.getId(), 0L));
        dto.setComments(context.comments().getOrDefault(event.getId(), 0L));
        dto.setViews(context.views().getOrDefault(event.getId(), 0L));
    }

    private void fill(EventFullDto dto, Event event, Context context) {
        dto.setInitiator(user(event, context.users()));
        dto.setConfirmedRequests(context.requests().getOrDefault(event.getId(), 0L));
        dto.setComments(context.comments().getOrDefault(event.getId(), 0L));
        dto.setViews(context.views().getOrDefault(event.getId(), 0L));
    }

    private UserShortDto user(Event event, Map<Long, UserDto> users) {
        UserDto user = users.get(event.getInitiatorId());
        return user == null
                ? new UserShortDto(event.getInitiatorId(), "")
                : new UserShortDto(user.getId(), user.getName());
    }

    private record Context(
            Map<Long, UserDto> users,
            Map<Long, Long> requests,
            Map<Long, Long> comments,
            Map<Long, Long> views
    ) {
    }
}
