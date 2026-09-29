package ru.practicum.ewm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.ewm.dto.ParticipationRequestDto;
import ru.practicum.ewm.dto.UserDto;
import ru.practicum.ewm.dto.internal.EventDetailsDto;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.mapper.RequestMapper;
import ru.practicum.ewm.model.Request;
import ru.practicum.ewm.repository.RequestRepository;
import ru.practicum.ewm.service.impl.RequestServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceImplTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private RemoteLookupService remoteLookupService;

    @Mock
    private RequestMapper requestMapper;

    @InjectMocks
    private RequestServiceImpl requestService;

    @Test
    void addRequestAutoConfirmsWhenModerationIsDisabled() {
        EventDetailsDto event = event(2L, 10L, 5, false);
        when(remoteLookupService.getUser(1L)).thenReturn(new UserDto());
        when(remoteLookupService.getEvent(2L)).thenReturn(event);
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestMapper.toDto(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            return ParticipationRequestDto.builder().status(request.getStatus().name()).build();
        });

        ParticipationRequestDto result = requestService.addRequest(1L, 2L);

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void addRequestRejectsDuplicate() {
        when(remoteLookupService.getUser(1L)).thenReturn(new UserDto());
        when(remoteLookupService.getEvent(2L)).thenReturn(event(2L, 10L, 5, true));
        when(requestRepository.existsByEventIdAndRequesterId(2L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> requestService.addRequest(1L, 2L))
                .isInstanceOf(ConflictException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void addRequestRejectsInitiator() {
        when(remoteLookupService.getUser(1L)).thenReturn(new UserDto());
        when(remoteLookupService.getEvent(2L)).thenReturn(event(2L, 1L, 0, false));

        assertThatThrownBy(() -> requestService.addRequest(1L, 2L))
                .isInstanceOf(ConflictException.class);
    }

    private EventDetailsDto event(
            Long id,
            Long initiatorId,
            Integer participantLimit,
            Boolean requestModeration
    ) {
        return EventDetailsDto.builder()
                .id(id)
                .initiatorId(initiatorId)
                .state("PUBLISHED")
                .participantLimit(participantLimit)
                .requestModeration(requestModeration)
                .build();
    }
}
