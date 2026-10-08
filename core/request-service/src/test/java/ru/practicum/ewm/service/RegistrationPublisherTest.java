package ru.practicum.ewm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.ewm.model.RegistrationOutbox;
import ru.practicum.ewm.repository.RegistrationOutboxRepository;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionProto;
import ru.practicum.stats.client.CollectorClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationPublisherTest {
    @Mock
    private RegistrationOutboxRepository repository;
    @Mock
    private CollectorClient collector;
    @InjectMocks
    private RegistrationPublisher publisher;

    @Test
    void preservesOriginalTimestampAndDeletesAcknowledgedAction() {
        when(repository.findFirst100ByOrderByIdAsc()).thenReturn(List.of(action()));
        publisher.publish();
        ArgumentCaptor<UserActionProto> captor = ArgumentCaptor.forClass(UserActionProto.class);
        verify(collector).collect(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo(ActionTypeProto.ACTION_REGISTER);
        assertThat(captor.getValue().getUserId()).isEqualTo(1);
        assertThat(captor.getValue().getEventId()).isEqualTo(2);
        assertThat(captor.getValue().getTimestamp().getSeconds()).isEqualTo(100);
        verify(repository).deleteById(10L);
    }

    @Test
    void keepsUnacknowledgedActionForRetry() {
        when(repository.findFirst100ByOrderByIdAsc()).thenReturn(List.of(action()));
        doThrow(new IllegalStateException("Collector unavailable")).when(collector).collect(any());
        publisher.publish();
        verify(repository, never()).deleteById(any());
    }

    private RegistrationOutbox action() {
        return RegistrationOutbox.builder().id(10L).userId(1L).eventId(2L).timestamp(Instant.ofEpochSecond(100)).build();
    }
}
