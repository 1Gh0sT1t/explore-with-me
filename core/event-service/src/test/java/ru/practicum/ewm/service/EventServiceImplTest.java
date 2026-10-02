package ru.practicum.ewm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ru.practicum.ewm.client.CommentClient;
import ru.practicum.ewm.client.RequestClient;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.PublicEventSearchParams;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.repository.CategoryRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.service.impl.EventServiceImpl;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private RemoteUserService remoteUserService;

    @Mock
    private StatsHelperService statsHelperService;

    @Mock
    private RequestClient requestClient;

    @Mock
    private CommentClient commentClient;

    private EventServiceImpl eventService;

    @BeforeEach
    void setUp() {
        EventDtoAssembler assembler = new EventDtoAssembler(eventMapper, remoteUserService,
                requestClient, commentClient, statsHelperService);
        eventService = new EventServiceImpl(eventRepository, categoryRepository, eventMapper,
                assembler, remoteUserService, statsHelperService);
    }

    @Test
    void onlyAvailableReusesConfirmedCountsAfterFiltering() {
        Event available = Event.builder().id(1L).initiatorId(10L).participantLimit(3).build();
        Event full = Event.builder().id(2L).initiatorId(20L).participantLimit(2).build();
        Event unlimited = Event.builder().id(3L).initiatorId(10L).participantLimit(0).build();
        when(eventRepository.findAll(ArgumentMatchers.<Specification<Event>>any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(available, full, unlimited)));
        when(requestClient.getConfirmedRequests(List.of(1L, 2L, 3L)))
                .thenReturn(Map.of(1L, 2L, 2L, 2L, 3L, 10L));
        when(eventMapper.toShortDto(available)).thenReturn(EventShortDto.builder().id(1L).build());
        when(eventMapper.toShortDto(unlimited)).thenReturn(EventShortDto.builder().id(3L).build());

        List<EventShortDto> result = eventService.getPublicEvents(
                PublicEventSearchParams.builder().onlyAvailable(true).build());

        assertThat(result).extracting(EventShortDto::getId).containsExactly(1L, 3L);
        assertThat(result).extracting(EventShortDto::getConfirmedRequests).containsExactly(2L, 10L);
        verify(requestClient).getConfirmedRequests(anyList());
    }

    @Test
    void onlyAvailableReturnsEmptyListWithoutLoadingOtherCounters() {
        Event full = Event.builder().id(1L).initiatorId(10L).participantLimit(2).build();
        when(eventRepository.findAll(ArgumentMatchers.<Specification<Event>>any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(full)));
        when(requestClient.getConfirmedRequests(List.of(1L))).thenReturn(Map.of(1L, 2L));

        assertThat(eventService.getPublicEvents(PublicEventSearchParams.builder().onlyAvailable(true).build()))
                .isEmpty();

        verify(requestClient).getConfirmedRequests(anyList());
        verify(commentClient, never()).getPublishedCommentCounts(anyList());
        verify(remoteUserService, never()).getUsers(anyList());
    }

    @Test
    void publicEventsWithoutAvailabilityFilterLoadCountsOnce() {
        Event event = Event.builder().id(1L).initiatorId(10L).build();
        when(eventRepository.findAll(ArgumentMatchers.<Specification<Event>>any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(requestClient.getConfirmedRequests(List.of(1L))).thenReturn(Map.of(1L, 4L));
        when(eventMapper.toShortDto(event)).thenReturn(EventShortDto.builder().id(1L).build());

        List<EventShortDto> result = eventService.getPublicEvents(PublicEventSearchParams.builder().build());

        assertThat(result.getFirst().getConfirmedRequests()).isEqualTo(4L);
        verify(requestClient).getConfirmedRequests(anyList());
    }
}
