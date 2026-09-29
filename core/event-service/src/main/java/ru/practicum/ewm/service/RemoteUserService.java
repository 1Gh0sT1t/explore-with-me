package ru.practicum.ewm.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.client.UserClient;
import ru.practicum.ewm.dto.UserDto;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ServiceUnavailableException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RemoteUserService {

    private final UserClient userClient;

    public UserDto getUser(Long userId) {
        try {
            return userClient.getUser(userId);
        } catch (FeignException.NotFound exception) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        } catch (FeignException exception) {
            throw new ServiceUnavailableException("User service is unavailable", exception);
        }
    }

    public Map<Long, UserDto> getUsers(List<Long> userIds) {
        try {
            return userClient.getUsers(userIds);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }
}
