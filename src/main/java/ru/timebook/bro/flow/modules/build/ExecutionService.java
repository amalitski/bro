package ru.timebook.bro.flow.modules.build;

import com.google.common.base.Stopwatch;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.modules.git.GitRepository;
import ru.timebook.bro.flow.modules.taskTracker.Issue;
import ru.timebook.bro.flow.modules.taskTracker.TaskTracker;
import ru.timebook.bro.flow.modules.git.Merge;
import ru.timebook.bro.flow.utils.JsonUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExecutionService {
    private final List<TaskTracker> taskTrackers;
    private final List<GitRepository> gitRepositories;
    private final MergeService mergeService;
    private final Config config;
    private final BuildRepository buildRepository;
    private final ProjectRepository projectRepository;
    private final BuildHasProjectRepository buildHasProjectRepository;
    private final JsonUtil jsonUtil;

    @Data
    @Builder
    public static class Response {
        private List<Issue> issues;
        private List<Merge> merges;
    }

    public ExecutionService(MergeService mergeService,
                            Config config,
                            BuildRepository buildRepository,
                            ProjectRepository projectRepository,
                            BuildHasProjectRepository buildHasProjectRepository,
                            Map<String, TaskTracker> taskTrackers,
                            Map<String, GitRepository> gitRepositories,
                            JsonUtil jsonUtil
    ) {
        this.mergeService = mergeService;
        this.config = config;
        this.buildRepository = buildRepository;
        this.projectRepository = projectRepository;
        this.buildHasProjectRepository = buildHasProjectRepository;
        this.taskTrackers = taskTrackers.values().stream().filter(TaskTracker::isEnabled).collect(Collectors.toList());
        this.gitRepositories = gitRepositories.values().stream().filter(GitRepository::isEnabled).collect(Collectors.toList());
        this.jsonUtil = jsonUtil;
    }

    public boolean validate() {
        var errors = new ArrayList<String>();
        if (config.getStage().getBranchName().isEmpty() || !config.getStage().getBranchName().matches("[^/]+/.+")) {
            errors.add("Branch name empty or doesn't match the mask ({stage}/{branchName}). Nonsense protection.");
        }
        if (config.getStage().getPushCmd().trim().isEmpty()) {
            errors.add("Push command will not be empty. Set correct value.");
        }
        if (config.getStage().getMergeCmd().trim().isEmpty()) {
            errors.add("Merge command will not be empty. Set correct value.");
        }
        errors.forEach(e -> log.error("{}", e));
        return errors.isEmpty();
    }

    public void mergeAndPush() {
        log.debug("Start");
        var timer = Stopwatch.createStarted();
        var issues = new ArrayList<Issue>();
        taskTrackers.forEach((v) -> issues.addAll(v.getForMerge()));
        gitRepositories.forEach((v) -> v.getInfo(issues));

        var merges = gitRepositories.stream().map(v -> v.getMerge(issues)).flatMap(Collection::stream).collect(Collectors.toList());
        mergeService.merge(merges);
        if (mergeService.needUpdate(merges, issues)) {
            mergeService.push(merges);
            issues.forEach(i -> i.getPullRequests().forEach(pr -> mergeService.getBranchByPr(pr, merges).ifPresent(pr::setBranch)));
            issues.forEach(mergeService::updateCommitters);
            mergeService.deployInfo(merges);
            createBuild(issues, merges);
        }
        mergeService.clean();
        log.debug("Complete: {}", timer.stop());
    }

    public void checkJobAndUpdateIssue() {
        var build = mergeService.checkJob();
        if (build.isEmpty()) {
            return;
        }
        var b = build.get();
        try {
            var issues = List.of(jsonUtil.deserialize(b.getIssuesJson(), Issue[].class));
            issues.stream().filter(i -> i.isDeployedSuccessful() && !i.isIssueUpdated()).forEach(i -> {
                taskTrackers.forEach(t -> {
                    if (i.getTaskTrackerClassName().equals(t.getClass().getName())) {
                        t.setDeployed(i);
                    }
                });
            });
            b.setIssuesJson(jsonUtil.serialize(issues));
            buildRepository.save(b);
        } catch (IOException e) {
            log.error("Deserialize issues catch exception", e);
        }
    }

    private void createBuild(List<Issue> issues, List<Merge> merges) {
        var bItem = Build.builder()
                .issuesJson(jsonUtil.serialize(issues))
                .hash(mergeService.getBuildHash(merges, issues))
                .startAt(LocalDateTime.now())
                .build();
        var b = buildRepository.save(bItem);
        var buildHasProjects = merges.stream().map(m -> {
            var p = projectRepository.findByName(m.getProjectName())
                    .orElse(Project.builder().name(m.getProjectName()).build());
            p.setBuildCheckSum(m.getCheckSum());
            p = projectRepository.save(p);
            return BuildHasProject.builder()
                    .project(p)
                    .build(b)
                    .mergesJson(jsonUtil.serialize(m))
                    .jobId(m.getPush().getDeploy().getJobId())
                    .jobStatus(m.getPush().getDeploy().getJobStatus())
                    .pushed(m.getPush().isPushed()).build();
        }).filter(Objects::nonNull).collect(Collectors.toList());
        buildHasProjectRepository.saveAll(buildHasProjects);
        log.trace("Build saved. Id: {}, buildHasProjects.size(): {}", b.getId(), buildHasProjects.size());
    }

    public String getOut(List<Issue> issues, List<Merge> merges) {
        var out = new StringBuilder();
        out.append(String.format("Issues: %s ", issues.size())).append("\n");
        for (var i : issues) {
            out.append(String.format("#%s %s %s", i.getId(), i.getSubject(), prInfoRow(i))).append("\n");
        }
        out.append("---\t Branches").append("\n");
        for (var m : merges) {
            out.append(String.format("  -\t %s (%s/%s)", m.getProjectName(), m.getBranches().stream().filter(Merge.Branch::isMergeLocal).count(), m.getBranches().size())).append("\n");
            m.getBranches().forEach(b -> out.append(String.format("\t branchName: %s, success: %s", b.getBranchName(), b.isMergeLocal())).append("\n"));
        }
        out.append("---\t Logs").append("\n");
        for (var m : merges) {
            out.append(String.format("  -\t %s:%s", m.getProjectName(), config.getStage().getBranchName())).append("\n");
        }
        out.append("---\t initStdOut").append("\n");
        for (var m : merges) {
            out.append(String.format("  -\t %s:%s", m.getProjectName(), config.getStage().getBranchName())).append("\n");
            out.append(String.format("   \t %s", m.getInitStdout())).append("\n");
        }
        return out.toString();
    }

    private String prInfoRow(Issue i) {
        var brs = i.getPullRequests().stream().filter(pr -> pr.getSourceBranchName() != null)
                .map(pr -> pr.getName() + ":" + pr.getBranch().getBranchName() + ":" + pr.getBranch().isMergeLocal())
                .collect(Collectors.joining(", "));
        if (brs.isEmpty()) {
            return "";
        }
        return "(" + brs + ")";
    }
}
