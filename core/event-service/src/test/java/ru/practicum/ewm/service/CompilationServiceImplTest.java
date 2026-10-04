package ru.practicum.ewm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.practicum.ewm.dto.CompilationDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.mapper.CompilationMapper;
import ru.practicum.ewm.model.Category;
import ru.practicum.ewm.model.Compilation;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.service.impl.CompilationServiceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@Import(CompilationServiceImpl.class)
class CompilationServiceImplTest {

    @Autowired
    private CompilationService compilationService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockBean
    private CompilationMapper compilationMapper;

    @MockBean
    private EventDtoAssembler eventDtoAssembler;

    @BeforeEach
    void setUp() {
        for (int i = 0; i < 4; i++) {
            Category category = Category.builder().name("Category " + i).build();
            entityManager.persist(category);
            List<Event> events = new ArrayList<>();
            for (int j = 0; j < 2; j++) {
                Event event = Event.builder().title("Event " + i + "-" + j).annotation("Annotation")
                        .description("Description").eventDate(LocalDateTime.now().plusDays(1))
                        .initiatorId(1L).category(category).build();
                entityManager.persist(event);
                events.add(event);
            }
            entityManager.persist(Compilation.builder().title("Compilation " + i)
                    .pinned(i % 2 == 0).events(events).build());
        }
        entityManager.persist(Compilation.builder().title("Empty").pinned(false).events(List.of()).build());
        entityManager.flush();
        entityManager.clear();
        when(compilationMapper.toDto(any())).thenAnswer(invocation -> {
            Compilation compilation = invocation.getArgument(0);
            return CompilationDto.builder().id(compilation.getId()).title(compilation.getTitle()).build();
        });
        when(eventDtoAssembler.toShortDtos(anyList())).thenAnswer(invocation -> {
            List<Event> events = invocation.getArgument(0);
            events.forEach(event -> assertThat(event.getCategory().getName()).startsWith("Category"));
            return events.stream().map(event -> EventShortDto.builder().id(event.getId()).build()).toList();
        });
    }

    @Test
    void queryCountDoesNotGrowWithPageSize() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        List<CompilationDto> single = compilationService.getCompilations(null, page(0, 1));
        long singlePageQueries = statistics.getPrepareStatementCount();
        entityManager.clear();
        statistics.clear();

        List<CompilationDto> multiple = compilationService.getCompilations(null, page(0, 3));

        assertThat(single).hasSize(1);
        assertThat(multiple).extracting(CompilationDto::getTitle)
                .containsExactly("Compilation 0", "Compilation 1", "Compilation 2");
        assertThat(multiple).allSatisfy(dto -> assertThat(dto.getEvents()).hasSize(2));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(singlePageQueries).isEqualTo(3);
    }

    @Test
    void pinnedFilterAndPaginationKeepTheExpectedCompilation() {
        List<CompilationDto> result = compilationService.getCompilations(true, page(1, 1));

        assertThat(result).extracting(CompilationDto::getTitle).containsExactly("Compilation 2");
        assertThat(result.getFirst().getEvents()).hasSize(2);
    }

    @Test
    void compilationWithoutEventsIsNotLostByBulkQuery() {
        List<CompilationDto> result = compilationService.getCompilations(null, page(4, 1));

        assertThat(result).extracting(CompilationDto::getTitle).containsExactly("Empty");
        assertThat(result.getFirst().getEvents()).isEmpty();
    }

    @Test
    void emptyPageDoesNotLoadEvents() {
        assertThat(compilationService.getCompilations(null, page(10, 1))).isEmpty();

        verify(eventDtoAssembler, never()).toShortDtos(anyList());
    }

    private PageRequest page(int number, int size) {
        return PageRequest.of(number, size, Sort.by("id"));
    }
}
