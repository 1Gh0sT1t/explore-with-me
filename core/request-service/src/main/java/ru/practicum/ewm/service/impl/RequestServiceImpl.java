package ru.practicum.ewm.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.dto.ParticipationRequestDto;
import ru.practicum.ewm.dto.internal.EventDetailsDto;
import ru.practicum.ewm.dto.internal.UserEventKey;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.RequestMapper;
import ru.practicum.ewm.model.Request;
import ru.practicum.ewm.model.RequestStatus;
import ru.practicum.ewm.repository.RequestRepository;
import ru.practicum.ewm.service.RemoteLookupService;
import ru.practicum.ewm.service.RequestService;
import ru.practicum.ewm.service.RequestStatusUpdater;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final RemoteLookupService remoteLookupService;
    private final RequestMapper requestMapper;
    private final RequestStatusUpdater requestStatusUpdater;

    @Override
    public ParticipationRequestDto addRequest(Long userId, Long eventId) {
        log.info("User id={} requests participation in event id={}", userId, eventId);
        remoteLookupService.getUser(userId);
        EventDetailsDto event = remoteLookupService.getEvent(eventId);

        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Event initiator cannot request participation in own event");
        }
        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictException("Cannot participate in unpublished event");
        }
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Request already exists");
        }

        int limit = event.getParticipantLimit() == null ? 0 : event.getParticipantLimit();
        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (limit > 0 && confirmed >= limit) {
            throw new ConflictException("The participant limit has been reached");
        }

        boolean autoConfirm = limit == 0 || Boolean.FALSE.equals(event.getRequestModeration());
        Request request = Request.builder()
                .created(LocalDateTime.now())
                .eventId(eventId)
                .requesterId(userId)
                .status(autoConfirm ? RequestStatus.CONFIRMED : RequestStatus.PENDING)
                .build();

        return requestMapper.toDto(requestRepository.save(request));
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        remoteLookupService.getUser(userId);
        return requestMapper.toDtoList(requestRepository.findByRequesterId(userId));
    }

    @Override
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        remoteLookupService.getUser(userId);
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " was not found"));
        if (!request.getRequesterId().equals(userId)) {
            throw new NotFoundException("Request with id=" + requestId + " was not found");
        }
        request.setStatus(RequestStatus.CANCELED);
        return requestMapper.toDto(requestRepository.save(request));
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        EventDetailsDto event = remoteLookupService.getEvent(eventId);
        checkInitiator(userId, event);
        return requestMapper.toDtoList(requestRepository.findByEventId(eventId));
    }

    @Override
    public EventRequestStatusUpdateResult updateRequestsStatus(
            UserEventKey key,
            EventRequestStatusUpdateRequest updateRequest
    ) {
        EventDetailsDto event = remoteLookupService.getEvent(key.eventId());
        checkInitiator(key.userId(), event);
        return requestStatusUpdater.update(event, updateRequest);
    }

    private void checkInitiator(Long userId, EventDetailsDto event) {
        if (!event.getInitiatorId().equals(userId)) {
            throw new NotFoundException("Event with id=" + event.getId() + " was not found");
        }
    }
}
