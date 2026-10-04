package ru.practicum.ewm.controller.internalapi;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.ewm.model.CommentStatus;
import ru.practicum.ewm.repository.CommentRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/comments")
public class InternalCommentController {

    private final CommentRepository commentRepository;

    @PostMapping("/counts")
    public Map<Long, Long> getPublishedCommentCounts(@RequestBody List<Long> eventIds) {
        return commentRepository.countByEventIdsAndStatus(eventIds, CommentStatus.PUBLISHED)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
