package ru.practicum.ewm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.model.Request;
import ru.practicum.ewm.model.RequestStatus;
import ru.practicum.ewm.repository.RegistrationOutboxRepository;
import ru.practicum.ewm.repository.RequestRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(showSql = false, properties = {"spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(RequestRegistrationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RequestRegistrationServiceTest {
    @Autowired
    private RequestRegistrationService service;
    @Autowired
    private RequestRepository requests;
    @SpyBean
    private RegistrationOutboxRepository outbox;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        requests.deleteAll();
    }

    @Test
    void savesRequestAndRegistrationAtomically() {
        Request saved = service.save(request());
        assertThat(requests.existsById(saved.getId())).isTrue();
        assertThat(outbox.findAll()).singleElement().satisfies(action -> {
            assertThat(action.getUserId()).isEqualTo(1);
            assertThat(action.getEventId()).isEqualTo(2);
            assertThat(action.getTimestamp()).isNotNull();
        });
    }

    @Test
    void rollsBackRequestIfOutboxCannotBeSaved() {
        doThrow(new IllegalStateException("Outbox unavailable")).when(outbox).save(any());
        assertThatThrownBy(() -> service.save(request())).isInstanceOf(IllegalStateException.class);
        assertThat(requests.count()).isZero();
    }

    private Request request() {
        return Request.builder().requesterId(1L).eventId(2L).created(LocalDateTime.now())
                .status(RequestStatus.CONFIRMED).build();
    }
}
