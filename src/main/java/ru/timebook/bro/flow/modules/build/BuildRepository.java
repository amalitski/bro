package ru.timebook.bro.flow.modules.build;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BuildRepository extends CrudRepository<Build, Long> {
//    Optional<Build> findByName(String name);
}
