package ru.practicum.ewm.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.dto.AdminEventSearchParams;
import ru.practicum.ewm.dto.EventFullDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.NewEventDto;
import ru.practicum.ewm.dto.PublicEventSearchParams;
import ru.practicum.ewm.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.dto.UpdateEventUserRequest;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.Category;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.EventState;
import ru.practicum.ewm.model.Location;
import ru.practicum.ewm.repository.CategoryRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.service.EventDtoAssembler;
import ru.practicum.ewm.service.EventService;
import ru.practicum.ewm.service.RemoteUserService;
import ru.practicum.ewm.service.StatsHelperService;
import ru.practicum.ewm.specification.EventSpecification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MIN_HOURS_BEFORE_EVENT = 2L;

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;
    private final EventDtoAssembler eventDtoAssembler;
    private final RemoteUserService remoteUserService;
    private final StatsHelperService statsHelperService;

    @Override
    public List<EventShortDto> getPublicEvents(PublicEventSearchParams params) {
        LocalDateTime start = parseDate(params.getRangeStart());
        LocalDateTime end = parseDate(params.getRangeEnd());
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("Field: rangeEnd. Error: rangeEnd должен быть позже rangeStart.");
        }
        if (start == null && end == null) {
            start = LocalDateTime.now();
        }

        Pageable pageable = PageRequest.of(
                params.getFrom() / params.getSize(),
                params.getSize(),
                Sort.by(Sort.Direction.ASC, "eventDate")
        );
        Specification<Event> specification = EventSpecification.publicFilter(
                normalizeText(params.getText()),
                emptyToNull(params.getCategories()),
                params.getPaid(),
                start,
                end
        );

        List<Event> events = eventRepository.findAll(specification, pageable).getContent();
        if (Boolean.TRUE.equals(params.getOnlyAvailable())) {
            Map<Long, Long> confirmed = eventDtoAssembler.getConfirmedRequests(events);
            events = events.stream()
                    .filter(event -> isAvailable(event, confirmed.getOrDefault(event.getId(), 0L)))
                    .toList();
        }

        statsHelperService.hit(params.getRequest());
        List<EventShortDto> result = eventDtoAssembler.toShortDtos(events);
        if ("VIEWS".equalsIgnoreCase(params.getSort())) {
            return result.stream()
                    .sorted(Comparator.comparing(EventShortDto::getViews).reversed())
                    .toList();
        }
        return result;
    }

    @Override
    public EventFullDto getPublicEvent(Long eventId, HttpServletRequest request) {
        Event event = getEvent(eventId);
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        statsHelperService.hit(request);
        return eventDtoAssembler.toFullDto(event);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        remoteUserService.getUser(userId);
        Pageable pageable = PageRequest.of(from / size, size);
        return eventDtoAssembler.toShortDtos(
                eventRepository.findByInitiatorId(userId, pageable).getContent()
        );
    }

    @Override
    public EventFullDto addEvent(Long userId, NewEventDto newEventDto) {
        remoteUserService.getUser(userId);
        Category category = getCategory(newEventDto.getCategory());
        checkEventDate(newEventDto.getEventDate());

        Event event = eventMapper.toEntity(newEventDto);
        event.setCategory(category);
        event.setInitiatorId(userId);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());
        return eventDtoAssembler.toFullDto(eventRepository.save(event));
    }

    @Override
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        remoteUserService.getUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        return eventDtoAssembler.toFullDto(event);
    }

    @Override
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        remoteUserService.getUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }
        if (updateRequest.getEventDate() != null) {
            checkEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case SEND_TO_REVIEW -> event.setState(EventState.PENDING);
                case CANCEL_REVIEW -> event.setState(EventState.CANCELED);
            }
        }

        applyUpdate(event, updateRequest.getAnnotation(), updateRequest.getDescription(),
                updateRequest.getTitle(), updateRequest.getCategory(), updateRequest.getPaid(),
                updateRequest.getParticipantLimit(), updateRequest.getRequestModeration(),
                updateRequest.getLocation(), updateRequest.getEventDate());
        return eventDtoAssembler.toFullDto(eventRepository.save(event));
    }

    @Override
    public List<EventFullDto> searchEventsByAdmin(AdminEventSearchParams params) {
        List<EventState> states = params.getStates() == null
                ? null
                : params.getStates().stream().map(EventState::valueOf).toList();
        int from = Math.max(params.getFrom(), 0);
        int size = params.getSize() <= 0 ? 10 : params.getSize();
        Pageable pageable = PageRequest.of(from / size, size, Sort.by(Sort.Direction.ASC, "eventDate"));
        Specification<Event> specification = EventSpecification.adminFilter(
                params.getUsers(), states, params.getCategories(), params.getRangeStart(), params.getRangeEnd());
        return eventDtoAssembler.toFullDtos(eventRepository.findAll(specification, pageable).getContent());
    }

    @Override
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = getEvent(eventId);
        if (updateRequest.getEventDate() != null) {
            checkEventDate(updateRequest.getEventDate());
        }

        applyUpdate(event, updateRequest.getAnnotation(), updateRequest.getDescription(),
                updateRequest.getTitle(), updateRequest.getCategory(), updateRequest.getPaid(),
                updateRequest.getParticipantLimit(), updateRequest.getRequestModeration(),
                updateRequest.getLocation(), updateRequest.getEventDate());

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case PUBLISH_EVENT -> {
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException(
                                "Cannot publish the event because it's not in the right state: " + event.getState());
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                }
                case REJECT_EVENT -> {
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException(
                                "Cannot reject the event because it's not in the right state: " + event.getState());
                    }
                    event.setState(EventState.CANCELED);
                }
            }
        }
        return eventDtoAssembler.toFullDto(eventRepository.save(event));
    }

    private void applyUpdate(
            Event event,
            String annotation,
            String description,
            String title,
            Long categoryId,
            Boolean paid,
            Integer participantLimit,
            Boolean requestModeration,
            ru.practicum.ewm.dto.LocationDto location,
            LocalDateTime eventDate
    ) {
        if (annotation != null) {
            event.setAnnotation(annotation);
        }
        if (description != null) {
            event.setDescription(description);
        }
        if (title != null) {
            event.setTitle(title);
        }
        if (categoryId != null) {
            event.setCategory(getCategory(categoryId));
        }
        if (paid != null) {
            event.setPaid(paid);
        }
        if (participantLimit != null) {
            event.setParticipantLimit(participantLimit);
        }
        if (requestModeration != null) {
            event.setRequestModeration(requestModeration);
        }
        if (location != null) {
            if (event.getLocation() == null) {
                event.setLocation(new Location());
            }
            event.getLocation().setLat(location.getLat());
            event.getLocation().setLon(location.getLon());
        }
        if (eventDate != null) {
            event.setEventDate(eventDate);
        }
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + categoryId + " was not found"));
    }

    private void checkEventDate(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new IllegalArgumentException(
                    "Field: eventDate. Error: должно содержать дату, которая еще не наступила.");
        }
    }

    private boolean isAvailable(Event event, long confirmedRequests) {
        Integer limit = event.getParticipantLimit();
        return limit == null || limit == 0 || confirmedRequests < limit;
    }

    private LocalDateTime parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value, FORMATTER);
    }

    private String normalizeText(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private List<Long> emptyToNull(List<Long> values) {
        return values == null || values.isEmpty() ? null : values;
    }
}
