package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.EventCommentKey;
import ru.practicum.ewm.dto.EventCommentSearchParams;
import ru.practicum.ewm.dto.NewCommentDto;
import ru.practicum.ewm.dto.UpdateCommentDto;
import ru.practicum.ewm.dto.UserCommentKey;
import ru.practicum.ewm.dto.internal.UserEventKey;

import java.util.List;

public interface CommentService {

    CommentDto addComment(UserEventKey key, NewCommentDto newCommentDto);

    List<CommentDto> getUserComments(Long userId, Pageable pageable);

    CommentDto updateComment(UserCommentKey key, UpdateCommentDto updateCommentDto);

    void deleteComment(Long userId, Long commentId);

    List<CommentDto> getEventComments(EventCommentSearchParams params, HttpServletRequest request);

    CommentDto getEventComment(EventCommentKey key, HttpServletRequest request);

    // Административные методы

    List<CommentDto> getAllComments(String status, Pageable pageable);

    CommentDto publishComment(Long commentId);

    CommentDto rejectComment(Long commentId);

    void deleteCommentByAdmin(Long commentId);
}
