package ru.practicum.ewm.dto;

import java.time.LocalDateTime;

public interface EventUpdateFields {

    String getAnnotation();

    String getDescription();

    String getTitle();

    Long getCategory();

    Boolean getPaid();

    Integer getParticipantLimit();

    Boolean getRequestModeration();

    LocationDto getLocation();

    LocalDateTime getEventDate();
}
