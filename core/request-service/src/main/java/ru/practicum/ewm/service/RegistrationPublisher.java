package ru.practicum.ewm.service;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.repository.RegistrationOutboxRepository;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionProto;
import ru.practicum.stats.client.CollectorClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationPublisher {
    private final RegistrationOutboxRepository repository;
    private final CollectorClient collector;

    @Scheduled(fixedDelayString = "${stats.registration-publish-delay:1000}")
    public void publish() {
        for (var action : repository.findFirst100ByOrderByIdAsc()) {
            try {
                collector.collect(UserActionProto.newBuilder().setUserId(action.getUserId())
                        .setEventId(action.getEventId()).setActionType(ActionTypeProto.ACTION_REGISTER)
                        .setTimestamp(Timestamp.newBuilder().setSeconds(action.getTimestamp().getEpochSecond())
                                .setNanos(action.getTimestamp().getNano())).build());
                repository.deleteById(action.getId());
            } catch (RuntimeException exception) {
                log.warn("Cannot publish registration for user={} event={}", action.getUserId(), action.getEventId(),
                        exception);
                break;
            }
        }
    }
}
