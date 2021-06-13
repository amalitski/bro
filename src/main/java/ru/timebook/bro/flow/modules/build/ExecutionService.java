package ru.timebook.bro.flow.modules.build;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.configurations.Configuration;
import ru.timebook.bro.flow.modules.git.GitRepository;
import ru.timebook.bro.flow.modules.taskTracker.Issue;
import ru.timebook.bro.flow.modules.taskTracker.TaskTracker;
import ru.timebook.bro.flow.modules.git.Merge;
import ru.timebook.bro.flow.utils.JsonUtil;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class ExecutionService {
    private final List<TaskTracker> taskTrackers;
    private final List<GitRepository> gitRepositories;
    private final MergeService mergeService;
    private final Configuration configuration;
    private final BuildRepository buildRepository;
    private final ProjectRepository projectRepository;
    private final BuildHasProjectRepository buildHasProjectRepository;

    @Data
    @Builder
    public static class Response {
        private List<Issue> issues;
        private List<Merge> merges;
    }

    public ExecutionService(MergeService mergeService,
                            Configuration configuration,
                            BuildRepository buildRepository,
                            ProjectRepository projectRepository,
                            BuildHasProjectRepository buildHasProjectRepository,
                            Map<String, TaskTracker> taskTrackers,
                            Map<String, GitRepository> gitRepositories) {
        this.mergeService = mergeService;
        this.configuration = configuration;
        this.buildRepository = buildRepository;
        this.projectRepository = projectRepository;
        this.buildHasProjectRepository = buildHasProjectRepository;
        this.taskTrackers = taskTrackers.values().stream().filter(TaskTracker::isEnabled).collect(Collectors.toList());
        this.gitRepositories = gitRepositories.values().stream().filter(GitRepository::isEnabled).collect(Collectors.toList());
    }

    public Response mergeAndPush() throws Exception {
        log.debug("Start");
        var issues = new ArrayList<Issue>();
        taskTrackers.forEach((v) -> issues.addAll(v.getForMerge()));
        gitRepositories.forEach((v) -> v.getInfo(issues));

        var merges = gitRepositories.stream().map(v -> v.getMerge(issues)).flatMap(Collection::stream).collect(Collectors.toList());
        mergeService.merge(merges);
//        mergeService.push(merges);

        issues.forEach(i -> i.getPullRequests().forEach(pr -> MergeService.getBranchByPr(pr, merges).ifPresent(pr::setBranch)));
        issues.forEach(MergeService::updateCommitters);
        createBuild(issues, merges);

        log.debug("Complete");
        return Response.builder().issues(issues).merges(merges).build();
    }

    private void  createBuild(List<Issue> issues, List<Merge> merges){
        var b = buildRepository.save(Build.builder().issuesJson(JsonUtil.serialize(issues)).startAt(LocalDateTime.now()).build());
        var buildHasProjects = merges.stream().map(m -> {
            var p = projectRepository.findByName(m.getProjectName()).orElse(Project.builder().name(m.getProjectName()).buildCheckSum("").build());
            if (p.getId() != null && m.getCheckSum() != null) {
                p.setBuildCheckSum(m.getCheckSum());
            }
            p = projectRepository.save(p);
            return BuildHasProject.builder().project(p).build(b).mergesJson(JsonUtil.serialize(m)).mergeCheckSum(m.getCheckSum()).build();
        }).filter(Objects::nonNull).collect(Collectors.toList());
        buildHasProjectRepository.saveAll(buildHasProjects);
        log.trace("Build saved. Id: {}, buildHasProjects.size(): {}", b.getId(), buildHasProjects.size());
    }

    public void setDeployed(List<Issue> issues) {
        Map<Class<? extends TaskTracker>, List<Issue>> trGroup = issues.stream().collect(Collectors.groupingBy(Issue::getTaskTrackerClazz));
        trGroup.forEach((k, v) -> {
            taskTrackers.stream()
                    .filter(e -> e.getClass().getName().equals(k.getName()))
                    .forEach(i -> i.setDeployed(v));
        });
    }

    public String getOut(List<Issue> issues, List<Merge> merges){
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
            out.append(String.format("  -\t %s:%s",  m.getProjectName(), configuration.getStage().getBranchName())).append("\n");
            out.append(String.format("   \t %s", m.getLog())).append("\n");
        }
        return out.toString();
    }

    private static String prInfoRow(Issue i) {
        var brs = i.getPullRequests().stream().filter(pr -> pr.getSourceBranchName() != null)
                .map(pr -> pr.getName() + ":" + pr.getBranch().getBranchName() + ":" + pr.getBranch().isMergeLocal())
                .collect(Collectors.joining(", "));
        if (brs.isEmpty()) {
            return "";
        }
        return "(" + brs + ")";
    }
}
