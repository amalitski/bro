package ru.timebook.bro.flow.modules.build;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.modules.git.Merge;
import ru.timebook.bro.flow.utils.DateTimeUtil;
import ru.timebook.bro.flow.utils.JsonUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlowService {
    private final BuildRepository buildRepository;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Issue extends ru.timebook.bro.flow.modules.taskTracker.Issue {
        @Data
        @EqualsAndHashCode(callSuper = true)
        public static class PullRequest extends ru.timebook.bro.flow.modules.taskTracker.Issue.PullRequest {
        }

        public String getMergeOut() {
            var out = this.getPullRequests().stream()
                    .filter(p -> p.getBranch() != null)
                    .map(p -> {
                        var stdOut = !StringUtils.isEmpty(p.getBranch().getStdout()) ? p.getBranch().getStdout() : "(empty)";
                        return p.getProjectName() + ":" + p.getBranch().getBranchName() + "\n" + stdOut;
                    }).collect(Collectors.joining("\n\n"));
            return out.trim();
        }
    }

    @Data
    @Builder
    public static class Build {
        private Long id;
        private int issuesSuccess;
        private int issuesFails;
        private String buildStartAt;
    }

    @Data
    @Builder
    public static class Response {
        private final List<Issue> issues;
        private final List<Merge> merges;
        private final List<Build> builds;
        private final Build lastBuild;
    }

    public FlowService(BuildRepository buildRepository) {
        this.buildRepository = buildRepository;
    }

    private Build getBuild(ru.timebook.bro.flow.modules.build.Build b) {
        try {
            var i = JsonUtil.deserialize(b.getIssuesJson(), Issue[].class);
            var iSuccess = Arrays.stream(i).filter(ru.timebook.bro.flow.modules.taskTracker.Issue::isMergeLocalSuccess).count();
            var iFails = i.length - iSuccess;
            return Build.builder().id(b.getId())
                    .issuesSuccess(Math.toIntExact(iSuccess))
                    .issuesFails(Math.toIntExact(iFails))
                    .buildStartAt(DateTimeUtil.formatFull(b.getStartAt()))
                    .build();
        } catch (IOException e) {
            log.error("Catch Exception", e);
            return null;
        }
    }

    public Response getLastBuild() throws IOException {
        var b = buildRepository.findFirstByOrderByStartAtDesc();
        if (b.isEmpty()) {
            return Response.builder()
                    .lastBuild(Build.builder().issuesSuccess(0).issuesFails(0).build())
                    .build();
        }
        var merges = b.get().getBuildHasProjects().stream().map(buildHasProject -> {
            try {
                return JsonUtil.deserialize(buildHasProject.getMergesJson(), Merge.class);
            } catch (IOException e) {
                log.error("Deserialize with exception", e);
            }
            return null;
        }).collect(Collectors.toList());
        var lastBuild = getBuild(b.get());
        var builds = buildRepository.findAllPushed(PageRequest.of(0, 5, Sort.by("startAt").descending())).stream()
                .map(this::getBuild).collect(Collectors.toList());
        var i = JsonUtil.deserialize(b.get().getIssuesJson(), Issue[].class);
        return Response.builder()
                .issues(Arrays.stream(i).toList())
                .merges(merges)
                .builds(builds)
                .lastBuild(lastBuild)
                .build();
    }
}
