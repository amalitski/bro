package ru.timebook.bro.flow.modules.build;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildRepository extends CrudRepository<Build, Long> {
    Optional<Build> findFirstByOrderByStartAtDesc();
    List<Build> findFirst5ByOrderByStartAtDesc();
}
