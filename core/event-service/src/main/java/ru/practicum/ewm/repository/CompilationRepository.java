package ru.practicum.ewm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.model.Compilation;

import java.util.List;

public interface CompilationRepository extends JpaRepository<Compilation, Long> {
    Page<Compilation> findAllByPinned(Boolean pinned, Pageable pageable);

    @Query("select distinct c from Compilation c left join fetch c.events e "
            + "left join fetch e.category where c.id in :ids")
    List<Compilation> findAllWithEventsByIdIn(@Param("ids") List<Long> ids);
}
