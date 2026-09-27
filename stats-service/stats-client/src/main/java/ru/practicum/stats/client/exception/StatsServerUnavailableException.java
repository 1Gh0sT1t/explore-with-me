package ru.practicum.stats.client.exception;

public class StatsServerUnavailableException extends RuntimeException {

    public StatsServerUnavailableException(String message) {
        super(message);
    }

    public StatsServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
