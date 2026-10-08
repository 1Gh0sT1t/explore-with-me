package ru.practicum.ewm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.ewm.model.RegistrationOutbox;

import java.util.List;

public interface RegistrationOutboxRepository extends JpaRepository<RegistrationOutbox, Long> {
    List<RegistrationOutbox> findFirst100ByOrderByIdAsc();
}
