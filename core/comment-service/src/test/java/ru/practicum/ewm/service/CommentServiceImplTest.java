package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.internal.EventDetailsDto;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CommentMapper;
import ru.practicum.ewm.model.Comment;
import ru.practicum.ewm.model.CommentStatus;
import ru.practicum.ewm.repository.CommentRepository;
import ru.practicum.ewm.service.impl.CommentServiceImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private RemoteLookupService remoteLookupService;

    @Mock
    private StatsHelperService statsHelperService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(remoteLookupService.getUsers(anyList()))
                .thenReturn(Map.of());
    }

    @Test
    void getEventCommentsReturnsPublishedComments() {
        Comment comment = comment(10L, CommentStatus.PUBLISHED);
        CommentDto dto = CommentDto.builder().id(10L).status("PUBLISHED").build();

        when(remoteLookupService.getEvent(2L)).thenReturn(new EventDetailsDto());
        when(commentRepository.findByEventIdAndStatus(eq(2L), eq(CommentStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));
        when(commentMapper.toDto(comment)).thenReturn(dto);

        List<CommentDto> result = commentService.getEventComments(2L, 0, 10, request);

        assertThat(result).extracting(CommentDto::getId).containsExactly(10L);
        verify(statsHelperService).hit(request);
    }

    @Test
    void getEventCommentsChecksEventInRemoteService() {
        when(remoteLookupService.getEvent(99L))
                .thenThrow(new NotFoundException("Event with id=99 was not found"));

        assertThatThrownBy(() -> commentService.getEventComments(99L, 0, 10, request))
                .isInstanceOf(NotFoundException.class);
        verify(statsHelperService, never()).hit(any());
    }

    @Test
    void getEventCommentRejectsUnpublishedComment() {
        Comment comment = comment(10L, CommentStatus.PENDING);
        when(commentRepository.findByIdAndEventId(10L, 2L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.getEventComment(2L, 10L, request))
                .isInstanceOf(NotFoundException.class);
        verify(statsHelperService, never()).hit(any());
    }

    @Test
    void getAllCommentsFiltersByStatus() {
        Comment comment = comment(10L, CommentStatus.PENDING);
        CommentDto dto = CommentDto.builder().id(10L).status("PENDING").build();
        when(commentRepository.findAllByStatus(eq(CommentStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));
        when(commentMapper.toDto(comment)).thenReturn(dto);

        assertThat(commentService.getAllComments("PENDING", 0, 10))
                .extracting(CommentDto::getStatus)
                .containsExactly("PENDING");
    }

    @Test
    void publishCommentChangesStatus() {
        Comment comment = comment(10L, CommentStatus.PENDING);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentMapper.toDto(any(Comment.class))).thenAnswer(invocation ->
                CommentDto.builder()
                        .id(10L)
                        .status(((Comment) invocation.getArgument(0)).getStatus().name())
                        .build());

        CommentDto result = commentService.publishComment(10L);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(comment.getUpdated()).isNotNull();
    }

    @Test
    void publishCommentRejectsWrongStatus() {
        Comment comment = comment(10L, CommentStatus.PUBLISHED);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.publishComment(10L))
                .isInstanceOf(ConflictException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteCommentByAdminDeletesExistingComment() {
        when(commentRepository.existsById(10L)).thenReturn(true);

        commentService.deleteCommentByAdmin(10L);

        verify(commentRepository).deleteById(10L);
    }

    @Test
    void deleteCommentByAdminRejectsUnknownComment() {
        when(commentRepository.existsById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> commentService.deleteCommentByAdmin(99L))
                .isInstanceOf(NotFoundException.class);
    }

    private Comment comment(Long id, CommentStatus status) {
        return Comment.builder()
                .id(id)
                .authorId(1L)
                .eventId(2L)
                .status(status)
                .build();
    }
}
