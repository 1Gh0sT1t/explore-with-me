package ru.practicum.ewm.dto;

import org.springframework.data.domain.Pageable;

public record EventCommentSearchParams(Long eventId, Pageable pageable) {
}
