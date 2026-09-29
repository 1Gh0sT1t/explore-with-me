package ru.practicum.ewm.service;

import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.client.EventClient;
import ru.practicum.ewm.client.UserClient;
import ru.practicum.ewm.dto.UserDto;
import ru.practicum.ewm.dto.internal.EventDetailsDto;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ServiceUnavailableException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RemoteLookupService {

    private final EventClient eventClient;
    private final UserClient userClient;

    @Retryable(retryFor = RetryableException.class,
            maxAttemptsExpression = "${service.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${service.retry.delay:500}"))
    public EventDetailsDto getEvent(Long eventId) {
        try {
            return eventClient.getEvent(eventId);
        } catch (FeignException.NotFound exception) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        } catch (RetryableException exception) {
            throw exception;
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("Event service is unavailable", exception);
        }
    }

    @Retryable(retryFor = RetryableException.class,
            maxAttemptsExpression = "${service.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${service.retry.delay:500}"))
    public UserDto getUser(Long userId) {
        try {
            return userClient.getUser(userId);
        } catch (FeignException.NotFound exception) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        } catch (RetryableException exception) {
            throw exception;
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("User service is unavailable", exception);
        }
    }

    @Retryable(retryFor = RetryableException.class,
            maxAttemptsExpression = "${service.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${service.retry.delay:500}"))
    public Map<Long, UserDto> getUsers(List<Long> userIds) {
        try {
            return userClient.getUsers(userIds);
        } catch (RetryableException exception) {
            throw exception;
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("User service is unavailable", exception);
        }
    }
}
