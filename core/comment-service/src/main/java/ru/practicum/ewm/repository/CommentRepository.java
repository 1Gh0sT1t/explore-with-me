package ru.practicum.ewm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.model.Comment;
import ru.practicum.ewm.model.CommentStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByEventIdAndStatus(Long eventId, CommentStatus status, Pageable pageable);

    Page<Comment> findByAuthorId(Long authorId, Pageable pageable);

    Optional<Comment> findByIdAndEventId(Long id, Long eventId);

    @Query("SELECT c FROM Comment c WHERE (:status IS NULL OR c.status = :status)")
    Page<Comment> findAllByStatus(@Param("status") CommentStatus status, Pageable pageable);

    @Query("""
            select c.eventId, count(c.id)
            from Comment c
            where c.eventId in :eventIds
              and c.status = :status
            group by c.eventId
            """)
    List<Object[]> countByEventIdsAndStatus(Collection<Long> eventIds, CommentStatus status);
}
