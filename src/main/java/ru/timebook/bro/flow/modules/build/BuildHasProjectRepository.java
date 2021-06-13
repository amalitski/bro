package ru.timebook.bro.flow.modules.build;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildHasProjectRepository extends CrudRepository<BuildHasProject, Long> {
}
