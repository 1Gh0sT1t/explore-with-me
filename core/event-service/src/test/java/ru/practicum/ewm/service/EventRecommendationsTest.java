package ru.practicum.ewm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.ewm.client.RequestClient;
import ru.practicum.ewm.dto.EventFullDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.internal.UserEventKey;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.EventState;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.service.impl.EventServiceImpl;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventRecommendationsTest {
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventDtoAssembler assembler;
    @Mock
    private RemoteUserService users;
    @Mock
    private StatsHelperService stats;
    @Mock
    private RequestClient requests;
    @InjectMocks
    private EventServiceImpl service;

    @Test
    void singleEventSendsViewForHeaderUser() {
        Event event = event(2);
        when(eventRepository.findById(2L)).thenReturn(Optional.of(event));
        when(assembler.toFullDto(event)).thenReturn(EventFullDto.builder().id(2L).rating(0.4).build());
        assertThat(service.getPublicEvent(2L, 1L).getRating()).isEqualTo(0.4);
        verify(stats).view(1, 2);
    }

    @Test
    void unpublishedEventDoesNotSendView() {
        Event event = event(2);
        event.setState(EventState.PENDING);
        when(eventRepository.findById(2L)).thenReturn(Optional.of(event));
        assertThatThrownBy(() -> service.getPublicEvent(2L, 1L)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(stats);
    }

    @Test
    void confirmedParticipantCanLikePastEvent() {
        when(eventRepository.findById(2L)).thenReturn(Optional.of(event(2)));
        when(requests.hasConfirmedRequest(1L, 2L)).thenReturn(true);
        service.likeEvent(new UserEventKey(1L, 2L));
        verify(stats).like(1, 2);
    }

    @Test
    void rejectsLikeWithoutConfirmedParticipation() {
        when(eventRepository.findById(2L)).thenReturn(Optional.of(event(2)));
        assertThatThrownBy(() -> service.likeEvent(new UserEventKey(1L, 2L)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(stats);
    }

    @Test
    void rejectsLikeBeforeEventTakesPlace() {
        Event future = event(2);
        future.setEventDate(LocalDateTime.now().plusDays(1));
        when(eventRepository.findById(2L)).thenReturn(Optional.of(future));
        assertThatThrownBy(() -> service.likeEvent(new UserEventKey(1L, 2L)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(stats);
    }

    @Test
    void recommendationsPreservePredictionOrderAndFilterUnpublishedEvents() {
        Event first = event(1);
        Event second = event(2);
        Event unpublished = event(3);
        unpublished.setState(EventState.PENDING);
        when(stats.recommendations(10, 3)).thenReturn(List.of(prediction(2), prediction(3), prediction(1)));
        when(eventRepository.findAllWithCategoryByIdIn(List.of(2L, 3L, 1L)))
                .thenReturn(List.of(first, unpublished, second));
        when(assembler.toShortDtos(List.of(second, first)))
                .thenReturn(List.of(EventShortDto.builder().id(2L).build(), EventShortDto.builder().id(1L).build()));
        assertThat(service.getRecommendations(10L, 3)).extracting(EventShortDto::getId).containsExactly(2L, 1L);
    }

    @Test
    void coldStartDoesNotQueryEvents() {
        assertThat(service.getRecommendations(10L, 10)).isEmpty();
        verify(eventRepository, never()).findAllWithCategoryByIdIn(any());
    }

    private Event event(long id) {
        return Event.builder().id(id).state(EventState.PUBLISHED).eventDate(LocalDateTime.now().minusDays(1)).build();
    }

    private RecommendedEventProto prediction(long id) {
        return RecommendedEventProto.newBuilder().setEventId(id).setScore(0.8).build();
    }
}
