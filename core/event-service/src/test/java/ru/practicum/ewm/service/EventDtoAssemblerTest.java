package ru.practicum.ewm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.ewm.client.CommentClient;
import ru.practicum.ewm.client.RequestClient;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.UserDto;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.Event;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDtoAssemblerTest {

    @Mock
    private EventMapper eventMapper;

    @Mock
    private RemoteUserService remoteUserService;

    @Mock
    private RequestClient requestClient;

    @Mock
    private CommentClient commentClient;

    @Mock
    private StatsHelperService statsHelperService;

    @InjectMocks
    private EventDtoAssembler assembler;

    @Test
    void enrichesListWithFixedNumberOfRemoteCalls() {
        Event first = Event.builder().id(1L).initiatorId(10L).build();
        Event second = Event.builder().id(2L).initiatorId(20L).build();
        when(eventMapper.toShortDto(first)).thenReturn(EventShortDto.builder().id(1L).build());
        when(eventMapper.toShortDto(second)).thenReturn(EventShortDto.builder().id(2L).build());
        when(remoteUserService.getUsers(List.of(10L, 20L))).thenReturn(Map.of(
                10L, new UserDto(10L, "first@test.ru", "First"),
                20L, new UserDto(20L, "second@test.ru", "Second")
        ));
        when(requestClient.getConfirmedRequests(List.of(1L, 2L))).thenReturn(Map.of(1L, 3L, 2L, 4L));
        when(commentClient.getPublishedCommentCounts(List.of(1L, 2L))).thenReturn(Map.of(1L, 5L, 2L, 6L));
        when(statsHelperService.getRatings(List.of(first, second))).thenReturn(Map.of(1L, 7.2, 2L, 8.4));

        List<EventShortDto> result = assembler.toShortDtos(List.of(first, second));

        assertThat(result).extracting(EventShortDto::getConfirmedRequests).containsExactly(3L, 4L);
        assertThat(result).extracting(EventShortDto::getComments).containsExactly(5L, 6L);
        assertThat(result).extracting(EventShortDto::getRating).containsExactly(7.2, 8.4);
        verify(requestClient, times(1)).getConfirmedRequests(anyList());
        verify(commentClient, times(1)).getPublishedCommentCounts(anyList());
        verify(remoteUserService, times(1)).getUsers(anyList());
    }
}
