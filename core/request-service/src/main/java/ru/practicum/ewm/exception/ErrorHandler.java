package ru.practicum.ewm.exception;

import feign.RetryableException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFoundException(NotFoundException exception) {
        return error(exception, "The required object was not found.", "NOT_FOUND");
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequestException(Exception exception) {
        return error(exception, "Incorrectly made request.", "BAD_REQUEST");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ConflictException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflictException(Exception exception) {
        return error(exception, "For the requested operation the conditions are not met.", "CONFLICT");
    }

    @ExceptionHandler({ServiceUnavailableException.class, RetryableException.class,
            ru.practicum.stats.client.exception.StatsServerUnavailableException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleServiceUnavailableException(Exception exception) {
        return error(exception, "Dependent service is unavailable.", "SERVICE_UNAVAILABLE");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleAllExceptions(Exception exception) {
        log.error("Internal server error", exception);
        return error(exception, "Internal server error.", "INTERNAL_SERVER_ERROR");
    }

    private ApiError error(Exception exception, String reason, String status) {
        return ApiError.builder()
                .errors(List.of(exception.getClass().getSimpleName()))
                .message(exception.getMessage())
                .reason(reason)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
