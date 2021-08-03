package ru.timebook.bro.flow.modules.build;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "buildHasProjects")
public class BuildHasProject {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "buildId")
    private Build build;

    @ManyToOne(optional = false)
    @JoinColumn(name = "projectId", nullable = false)
    private Project project;

    @Column(nullable = true)
    private String mergeCheckSum;

    @Column
    private String lastCommitSha;

    @Column
    private int jobId;

    @Column
    private boolean pushed;

    @Lob
    @Column(nullable = true)
    private String mergesJson;
}

