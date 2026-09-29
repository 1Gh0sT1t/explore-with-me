package ru.practicum.ewm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "comment-service", fallback = CommentClientFallback.class)
public interface CommentClient {

    @PostMapping("/internal/comments/counts")
    Map<Long, Long> getPublishedCommentCounts(@RequestBody List<Long> eventIds);
}
