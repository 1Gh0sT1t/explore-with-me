package ru.practicum.ewm.stats.analyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationRepositoryTest {
    private EmbeddedDatabase database;
    private RecommendationRepository repository;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2)
                .addScript("schema.sql").build();
        repository = new RecommendationRepository(new NamedParameterJdbcTemplate(database));
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.update("INSERT INTO user_interactions VALUES (1, 10, 0.8, '2026-01-01 00:00:00')");
        jdbc.update("INSERT INTO user_interactions VALUES (1, 20, 1.0, '2026-01-02 00:00:00')");
        jdbc.update("INSERT INTO user_interactions VALUES (2, 10, 0.4, '2026-01-02 00:00:00')");
        jdbc.update("INSERT INTO event_similarities VALUES (5, 10, 0.7, '2026-01-01 00:00:00')");
        jdbc.update("INSERT INTO event_similarities VALUES (20, 30, 0.9, '2026-01-01 00:00:00')");
        jdbc.update("INSERT INTO event_similarities VALUES (40, 50, 0.8, '2026-01-01 00:00:00')");
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void historyUsesLatestInteractionFirst() {
        assertThat(repository.history(1)).extracting(Interaction::eventId).containsExactly(20L, 10L);
    }

    @Test
    void similaritiesFindBothSidesButOnlyUsersHistory() {
        assertThat(repository.similaritiesForUser(1)).hasSize(2);
        assertThat(repository.similaritiesForEvent(10)).extracting(Similarity::eventA).containsExactly(5L);
    }

    @Test
    void countsSumWeightsAcrossUsers() {
        assertThat(repository.interactionCounts(List.of(10L, 20L))).containsEntry(10L, 1.2).containsEntry(20L, 1.0);
        assertThat(repository.interactionCounts(List.of())).isEmpty();
    }
}
