package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.dto.ParticipationRequestDto;
import ru.practicum.ewm.dto.RequestStatusAction;
import ru.practicum.ewm.dto.internal.EventDetailsDto;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.mapper.RequestMapper;
import ru.practicum.ewm.model.Request;
import ru.practicum.ewm.model.RequestStatus;
import ru.practicum.ewm.repository.RequestRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestStatusUpdater {

    private final RequestRepository requestRepository;
    private final RequestMapper requestMapper;

    @Transactional
    public EventRequestStatusUpdateResult update(EventDetailsDto event, EventRequestStatusUpdateRequest update) {
        if (participantLimit(event) == 0 || Boolean.FALSE.equals(event.getRequestModeration())) {
            return new EventRequestStatusUpdateResult(List.of(), List.of());
        }
        List<Request> requests = requestRepository.findByEventIdAndIdIn(event.getId(), update.getRequestIds());
        validateRequests(requests);
        if (update.getStatus() == RequestStatusAction.REJECTED) {
            return new EventRequestStatusUpdateResult(List.of(), rejectRequests(requests));
        }
        return confirmRequests(event, requests);
    }

    private void validateRequests(List<Request> requests) {
        for (Request request : requests) {
            if (request.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }
        }
    }

    private List<ParticipationRequestDto> rejectRequests(List<Request> requests) {
        requests.forEach(request -> request.setStatus(RequestStatus.REJECTED));
        requestRepository.saveAll(requests);
        return requestMapper.toDtoList(requests);
    }

    private EventRequestStatusUpdateResult confirmRequests(EventDetailsDto event, List<Request> requests) {
        long confirmed = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        long available = participantLimit(event) - confirmed;
        if (available <= 0) {
            throw new ConflictException("The participant limit has been reached");
        }
        List<ParticipationRequestDto> confirmedList = new ArrayList<>();
        List<ParticipationRequestDto> rejectedList = new ArrayList<>();
        for (Request request : requests) {
            if (available > 0) {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmedList.add(requestMapper.toDto(request));
                available--;
            } else {
                request.setStatus(RequestStatus.REJECTED);
                rejectedList.add(requestMapper.toDto(request));
            }
        }
        requestRepository.saveAll(requests);
        if (available == 0) {
            rejectedList.addAll(rejectRemainingPending(event.getId()));
        }
        return new EventRequestStatusUpdateResult(confirmedList, rejectedList);
    }

    private List<ParticipationRequestDto> rejectRemainingPending(Long eventId) {
        return rejectRequests(requestRepository.findByEventIdAndStatus(eventId, RequestStatus.PENDING));
    }

    private int participantLimit(EventDetailsDto event) {
        return event.getParticipantLimit() == null ? 0 : event.getParticipantLimit();
    }
}
