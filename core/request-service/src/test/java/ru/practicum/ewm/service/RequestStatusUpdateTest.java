package ru.practicum.ewm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.practicum.ewm.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.dto.ParticipationRequestDto;
import ru.practicum.ewm.dto.RequestStatusAction;
import ru.practicum.ewm.dto.internal.EventDetailsDto;
import ru.practicum.ewm.dto.internal.UserEventKey;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.RequestMapper;
import ru.practicum.ewm.mapper.RequestMapperImpl;
import ru.practicum.ewm.model.Request;
import ru.practicum.ewm.model.RequestStatus;
import ru.practicum.ewm.repository.RequestRepository;
import ru.practicum.ewm.service.impl.RequestServiceImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({RequestServiceImpl.class, RequestStatusUpdater.class, RequestMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RequestStatusUpdateTest {

    private static final UserEventKey KEY = new UserEventKey(10L, 2L);

    @Autowired
    private RequestService requestService;

    @Autowired
    private RequestRepository requestRepository;

    @MockBean
    private RemoteLookupService remoteLookupService;

    @SpyBean
    private RequestMapper requestMapper;

    @BeforeEach
    void setUp() {
        requestRepository.deleteAll();
    }

    @Test
    void remoteLookupRunsBeforeDatabaseTransaction() {
        Request pending = request(RequestStatus.PENDING);
        when(remoteLookupService.getEvent(2L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return event(2);
        });
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return invocation.callRealMethod();
        }).when(requestMapper).toDtoList(anyList());

        EventRequestStatusUpdateResult result = requestService.updateRequestsStatus(KEY,
                update(List.of(pending.getId()), RequestStatusAction.REJECTED));

        assertThat(result.getRejectedRequests()).hasSize(1);
        assertThat(status(pending)).isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    void confirmsWithinLimitAndRejectsAllRemainingPending() {
        Request existing = request(RequestStatus.CONFIRMED);
        Request first = request(RequestStatus.PENDING);
        Request second = request(RequestStatus.PENDING);
        Request remaining = request(RequestStatus.PENDING);
        when(remoteLookupService.getEvent(2L)).thenReturn(event(2));

        EventRequestStatusUpdateResult result = requestService.updateRequestsStatus(KEY,
                update(List.of(first.getId(), second.getId()), RequestStatusAction.CONFIRMED));

        assertThat(result.getConfirmedRequests()).hasSize(1);
        assertThat(result.getRejectedRequests()).hasSize(2);
        assertThat(result.getRejectedRequests()).extracting(ParticipationRequestDto::getId)
                .contains(remaining.getId());
        assertThat(requestRepository.countByEventIdAndStatus(2L, RequestStatus.CONFIRMED)).isEqualTo(2);
        assertThat(requestRepository.countByEventIdAndStatus(2L, RequestStatus.PENDING)).isZero();
        assertThat(status(existing)).isEqualTo(RequestStatus.CONFIRMED);
    }

    @Test
    void keepsOtherRequestsPendingWhenLimitIsNotReached() {
        Request selected = request(RequestStatus.PENDING);
        Request remaining = request(RequestStatus.PENDING);
        when(remoteLookupService.getEvent(2L)).thenReturn(event(3));

        EventRequestStatusUpdateResult result = requestService.updateRequestsStatus(KEY,
                update(List.of(selected.getId()), RequestStatusAction.CONFIRMED));

        assertThat(result.getConfirmedRequests()).extracting(ParticipationRequestDto::getId)
                .containsExactly(selected.getId());
        assertThat(result.getRejectedRequests()).isEmpty();
        assertThat(status(remaining)).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void rejectsOnlySelectedRequests() {
        Request selected = request(RequestStatus.PENDING);
        Request remaining = request(RequestStatus.PENDING);
        when(remoteLookupService.getEvent(2L)).thenReturn(event(2));

        EventRequestStatusUpdateResult result = requestService.updateRequestsStatus(KEY,
                update(List.of(selected.getId()), RequestStatusAction.REJECTED));

        assertThat(result.getConfirmedRequests()).isEmpty();
        assertThat(result.getRejectedRequests()).extracting(ParticipationRequestDto::getId)
                .containsExactly(selected.getId());
        assertThat(status(remaining)).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void rejectsNonPendingRequestWithoutChangingOtherRequests() {
        Request pending = request(RequestStatus.PENDING);
        Request canceled = request(RequestStatus.CANCELED);
        when(remoteLookupService.getEvent(2L)).thenReturn(event(2));

        assertThatThrownBy(() -> requestService.updateRequestsStatus(KEY,
                update(List.of(pending.getId(), canceled.getId()), RequestStatusAction.CONFIRMED)))
                .isInstanceOf(ConflictException.class);

        assertThat(status(pending)).isEqualTo(RequestStatus.PENDING);
        assertThat(status(canceled)).isEqualTo(RequestStatus.CANCELED);
    }

    @Test
    void rejectsConfirmationWhenLimitIsAlreadyReached() {
        request(RequestStatus.CONFIRMED);
        Request pending = request(RequestStatus.PENDING);
        when(remoteLookupService.getEvent(2L)).thenReturn(event(1));

        assertThatThrownBy(() -> requestService.updateRequestsStatus(KEY,
                update(List.of(pending.getId()), RequestStatusAction.CONFIRMED)))
                .isInstanceOf(ConflictException.class);

        assertThat(status(pending)).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void rejectsUserWhoIsNotTheInitiator() {
        Request pending = request(RequestStatus.PENDING);
        when(remoteLookupService.getEvent(2L)).thenReturn(event(2));

        assertThatThrownBy(() -> requestService.updateRequestsStatus(new UserEventKey(99L, 2L),
                update(List.of(pending.getId()), RequestStatusAction.REJECTED)))
                .isInstanceOf(NotFoundException.class);

        assertThat(status(pending)).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void rollsBackSavedRequestsWhenMappingFails() {
        Request pending = request(RequestStatus.PENDING);
        when(remoteLookupService.getEvent(2L)).thenReturn(event(2));
        doThrow(new IllegalStateException("Mapping failed")).when(requestMapper).toDtoList(anyList());

        assertThatThrownBy(() -> requestService.updateRequestsStatus(KEY,
                update(List.of(pending.getId()), RequestStatusAction.REJECTED)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(status(pending)).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void skipsUpdatesWhenModerationIsNotRequired() {
        Request pending = request(RequestStatus.PENDING);
        EventDetailsDto event = event(2);
        event.setRequestModeration(false);
        when(remoteLookupService.getEvent(2L)).thenReturn(event);

        EventRequestStatusUpdateResult result = requestService.updateRequestsStatus(KEY,
                update(List.of(pending.getId()), RequestStatusAction.CONFIRMED));

        assertThat(result.getConfirmedRequests()).isEmpty();
        assertThat(result.getRejectedRequests()).isEmpty();
        assertThat(status(pending)).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void skipsUpdatesForUnlimitedEvent() {
        Request pending = request(RequestStatus.PENDING);
        when(remoteLookupService.getEvent(2L)).thenReturn(event(0));

        EventRequestStatusUpdateResult result = requestService.updateRequestsStatus(KEY,
                update(List.of(pending.getId()), RequestStatusAction.CONFIRMED));

        assertThat(result.getConfirmedRequests()).isEmpty();
        assertThat(result.getRejectedRequests()).isEmpty();
        assertThat(status(pending)).isEqualTo(RequestStatus.PENDING);
    }

    private Request request(RequestStatus status) {
        return requestRepository.save(Request.builder().eventId(2L).requesterId(1L)
                .created(LocalDateTime.now()).status(status).build());
    }

    private RequestStatus status(Request request) {
        return requestRepository.findById(request.getId()).orElseThrow().getStatus();
    }

    private EventDetailsDto event(int limit) {
        return EventDetailsDto.builder().id(2L).initiatorId(10L).participantLimit(limit)
                .requestModeration(true).state("PUBLISHED").build();
    }

    private EventRequestStatusUpdateRequest update(List<Long> ids, RequestStatusAction status) {
        return new EventRequestStatusUpdateRequest(ids, status);
    }
}
