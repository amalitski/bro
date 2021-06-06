package ru.timebook.bro.flow.modules.build;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "builds")
public class Build {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Singular
    @OneToMany(mappedBy = "build")
    Set<BuildHasProject> buildHasProjects = new HashSet<>();
    @Lob
    @Column
    private String issuesJson;
    @Column(nullable = false)
    private LocalDateTime startAt;
    @Column
    private LocalDateTime completeAt;
}
