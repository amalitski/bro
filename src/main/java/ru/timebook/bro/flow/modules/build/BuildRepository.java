package ru.timebook.bro.flow.modules.build;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BuildRepository extends CrudRepository<Build, Long> {
    Optional<Build> findFirstByOrderByStartAtDesc();

    @Query(value = "SELECT b FROM Build b JOIN b.buildHasProjects bp WHERE bp.pushed = true GROUP BY b, b.hash")
    List<Build> findAllPushed(Pageable pageable);

    @Query(value = """
            SELECT b FROM Build b
            JOIN b.buildHasProjects bp
            JOIN bp.project p
            WHERE b.startAt >= :startAt AND
                bp.pushed = true AND
                bp.jobId IS NOT NULL
                GROUP BY b, b.hash
            """)
    List<Build> findFirstByPushedAndJobId(Pageable pageable, LocalDateTime startAt);

    @Query(value = """
            SELECT b FROM Build b JOIN b.buildHasProjects bp
            JOIN bp.project p
            WHERE b.startAt >= :startAt AND
                bp.pushed = true
                GROUP BY b, b.hash
            """)
    List<Build> findFirstByPushed(Pageable pageable, LocalDateTime startAt);

    List<Build> findAll(Pageable pageable);
}