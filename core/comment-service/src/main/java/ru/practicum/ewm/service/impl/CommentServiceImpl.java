package ru.practicum.ewm.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.NewCommentDto;
import ru.practicum.ewm.dto.UpdateCommentDto;
import ru.practicum.ewm.dto.UserDto;
import ru.practicum.ewm.dto.UserShortDto;
import ru.practicum.ewm.dto.internal.EventDetailsDto;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CommentMapper;
import ru.practicum.ewm.model.Comment;
import ru.practicum.ewm.model.CommentStatus;
import ru.practicum.ewm.repository.CommentRepository;
import ru.practicum.ewm.service.CommentService;
import ru.practicum.ewm.service.RemoteLookupService;
import ru.practicum.ewm.service.StatsHelperService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final RemoteLookupService remoteLookupService;
    private final StatsHelperService statsHelperService;

    @Override
    public CommentDto addComment(Long userId, Long eventId, NewCommentDto newCommentDto) {
        UserDto user = remoteLookupService.getUser(userId);
        EventDetailsDto event = remoteLookupService.getEvent(eventId);
        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictException("Only published events can be commented");
        }

        Comment comment = commentMapper.toEntity(newCommentDto);
        comment.setAuthorId(userId);
        comment.setEventId(eventId);
        comment.setStatus(CommentStatus.PENDING);
        comment.setCreated(LocalDateTime.now());

        return toDto(commentRepository.save(comment), user);
    }

    @Override
    public List<CommentDto> getUserComments(Long userId, int from, int size) {
        remoteLookupService.getUser(userId);
        Pageable pageable = PageRequest.of(from / size, size);
        return toDtos(commentRepository.findByAuthorId(userId, pageable).getContent());
    }

    @Override
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto updateCommentDto) {
        UserDto user = remoteLookupService.getUser(userId);
        Comment comment = getComment(commentId);
        if (!comment.getAuthorId().equals(userId)) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }
        if (comment.getStatus() == CommentStatus.PUBLISHED) {
            throw new ConflictException("Published comment cannot be updated");
        }
        if (comment.getStatus() == CommentStatus.DELETED) {
            throw new ConflictException("Deleted comment cannot be updated");
        }

        comment.setText(updateCommentDto.getText());
        comment.setUpdated(LocalDateTime.now());
        comment.setStatus(CommentStatus.PENDING);
        return toDto(commentRepository.save(comment), user);
    }

    @Override
    public void deleteComment(Long userId, Long commentId) {
        remoteLookupService.getUser(userId);
        Comment comment = getComment(commentId);
        if (!comment.getAuthorId().equals(userId)) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }
        comment.setStatus(CommentStatus.DELETED);
        comment.setUpdated(LocalDateTime.now());
        commentRepository.save(comment);
    }

    @Override
    public List<CommentDto> getEventComments(Long eventId, int from, int size, HttpServletRequest request) {
        remoteLookupService.getEvent(eventId);
        Pageable pageable = PageRequest.of(from / size, size, Sort.by(Sort.Direction.ASC, "created"));
        List<CommentDto> comments = toDtos(commentRepository
                .findByEventIdAndStatus(eventId, CommentStatus.PUBLISHED, pageable)
                .getContent());
        statsHelperService.hit(request);
        return comments;
    }

    @Override
    public CommentDto getEventComment(Long eventId, Long commentId, HttpServletRequest request) {
        Comment comment = commentRepository.findByIdAndEventId(commentId, eventId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
        if (comment.getStatus() != CommentStatus.PUBLISHED) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }
        statsHelperService.hit(request);
        return toDtos(List.of(comment)).getFirst();
    }

    @Override
    public List<CommentDto> getAllComments(String status, int from, int size) {
        CommentStatus commentStatus = CommentStatus.from(status);
        Pageable pageable = PageRequest.of(from / size, size, Sort.by(Sort.Direction.DESC, "created"));
        return toDtos(commentRepository.findAllByStatus(commentStatus, pageable).getContent());
    }

    @Override
    public CommentDto publishComment(Long commentId) {
        Comment comment = getComment(commentId);
        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Only comment with status PENDING can be published");
        }
        comment.setStatus(CommentStatus.PUBLISHED);
        comment.setUpdated(LocalDateTime.now());
        commentRepository.save(comment);
        return toDtos(List.of(comment)).getFirst();
    }

    @Override
    public CommentDto rejectComment(Long commentId) {
        Comment comment = getComment(commentId);
        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Only comment with status PENDING can be rejected");
        }
        comment.setStatus(CommentStatus.REJECTED);
        comment.setUpdated(LocalDateTime.now());
        commentRepository.save(comment);
        return toDtos(List.of(comment)).getFirst();
    }

    @Override
    public void deleteCommentByAdmin(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }
        commentRepository.deleteById(commentId);
    }

    private Comment getComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
    }

    private List<CommentDto> toDtos(List<Comment> comments) {
        List<Long> authorIds = comments.stream().map(Comment::getAuthorId).distinct().toList();
        Map<Long, UserDto> users = authorIds.isEmpty() ? Map.of() : remoteLookupService.getUsers(authorIds);
        return comments.stream()
                .map(comment -> toDto(comment, users.get(comment.getAuthorId())))
                .toList();
    }

    private CommentDto toDto(Comment comment, UserDto user) {
        CommentDto dto = commentMapper.toDto(comment);
        if (user != null) {
            dto.setAuthor(new UserShortDto(user.getId(), user.getName()));
        }
        return dto;
    }
}
