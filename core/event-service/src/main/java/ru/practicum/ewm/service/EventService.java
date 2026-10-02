package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import ru.practicum.ewm.dto.*;
import ru.practicum.ewm.dto.internal.UserEventKey;

import java.util.List;

public interface EventService {

    List<EventShortDto> getPublicEvents(PublicEventSearchParams params);

    EventFullDto getPublicEvent(Long eventId, HttpServletRequest request);

    List<EventShortDto> getUserEvents(Long userId, Pageable pageable);

    EventFullDto addEvent(Long userId, NewEventDto newEventDto);

    EventFullDto getUserEvent(Long userId, Long eventId);

    EventFullDto updateEventByUser(UserEventKey key, UpdateEventUserRequest updateRequest);

    List<EventFullDto> searchEventsByAdmin(AdminEventSearchParams params);

    EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest);
}
