package ru.timebook.bro.flow.modules.build;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildRepository extends CrudRepository<Build, Long> {
    Optional<Build> findFirstByOrderByStartAtDesc();
    @Query(value = "SELECT b FROM Build b JOIN b.buildHasProjects bp WHERE bp.pushed = true GROUP BY b")
    List<Build> findAllPushed(Pageable pageable);
}