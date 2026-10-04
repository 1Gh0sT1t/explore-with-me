package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.model.RegistrationOutbox;
import ru.practicum.ewm.model.Request;
import ru.practicum.ewm.repository.RegistrationOutboxRepository;
import ru.practicum.ewm.repository.RequestRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RequestRegistrationService {
    private final RequestRepository requestRepository;
    private final RegistrationOutboxRepository outboxRepository;

    @Transactional
    public Request save(Request request) {
        Request saved = requestRepository.save(request);
        outboxRepository.save(RegistrationOutbox.builder().userId(saved.getRequesterId())
                .eventId(saved.getEventId()).timestamp(Instant.now()).build());
        return saved;
    }
}
