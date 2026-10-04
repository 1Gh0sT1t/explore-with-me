package ru.practicum.ewm.controller.priv;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.dto.CommentDto;
import ru.practicum.ewm.dto.NewCommentDto;
import ru.practicum.ewm.dto.UpdateCommentDto;
import ru.practicum.ewm.dto.UserCommentKey;
import ru.practicum.ewm.dto.internal.UserEventKey;
import ru.practicum.ewm.service.CommentService;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class PrivateCommentController {

    private final CommentService commentService;

    @PostMapping("/users/{userId}/events/{eventId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@PathVariable Long userId,
                                 @PathVariable Long eventId,
                                 @Valid @RequestBody NewCommentDto newCommentDto) {
        log.info("POST /users/{}/events/{}/comments", userId, eventId);
        return commentService.addComment(new UserEventKey(userId, eventId), newCommentDto);
    }

    @GetMapping("/users/{userId}/comments")
    public List<CommentDto> getUserComments(@PathVariable Long userId,
                                            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
                                            @RequestParam(defaultValue = "10") @Positive int size) {
        log.info("GET /users/{}/comments - from={}, size={}", userId, from, size);
        return commentService.getUserComments(userId, PageRequest.of(from / size, size));
    }

    @PatchMapping("/users/{userId}/comments/{commentId}")
    public CommentDto updateComment(@PathVariable Long userId,
                                    @PathVariable Long commentId,
                                    @Valid @RequestBody UpdateCommentDto updateCommentDto) {
        log.info("PATCH /users/{}/comments/{}", userId, commentId);
        return commentService.updateComment(new UserCommentKey(userId, commentId), updateCommentDto);
    }

    @DeleteMapping("/users/{userId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long userId,
                              @PathVariable Long commentId) {
        log.info("DELETE /users/{}/comments/{}", userId, commentId);
        commentService.deleteComment(userId, commentId);
    }
}
