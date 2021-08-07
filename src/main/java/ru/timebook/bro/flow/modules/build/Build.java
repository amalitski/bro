package ru.timebook.bro.flow.modules.build;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
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
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "build", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BuildHasProject> buildHasProjects;
    @Lob
    @Column(nullable = false)
    private String issuesJson;
    @Column(nullable = false)
    private String hash;
    @Column(nullable = false)
    private LocalDateTime startAt;
    @Column
    private LocalDateTime completeAt;
}
