package ru.timebook.bro.flow.modules.build;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.modules.build.Build;
import ru.timebook.bro.flow.modules.build.BuildRepository;
import ru.timebook.bro.flow.modules.git.Merge;
import ru.timebook.bro.flow.modules.taskTracker.Issue;
import ru.timebook.bro.flow.modules.taskTracker.TaskTracker;
import ru.timebook.bro.flow.utils.DateTimeUtil;
import ru.timebook.bro.flow.utils.JsonUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlowService {
    private final BuildRepository buildRepository;

    public static class Issue extends ru.timebook.bro.flow.modules.taskTracker.Issue {
        private List<ru.timebook.bro.flow.modules.taskTracker.Issue.PullRequest> pullRequests;
        public static class PullRequest extends ru.timebook.bro.flow.modules.taskTracker.Issue.PullRequest {
        }

        public String getMergeOut() {
            var out = this.getPullRequests().stream()
                    .filter(p -> p.getBranch() != null)
                    .map(p -> p.getProjectName() + ":" + p.getBranch().getBranchName() + "\n" + p.getBranch().getStdout().trim())
                    .collect(Collectors.joining("\n\n"));
            return out.trim();
        }
    }

    @Data
    @Builder
    public static class Response {
        private final Issue[] issues;
        private final int issuesSuccess;
        private final int issuesFails;
        private final String buildStartAt;
        private final String builCompleteAt;
    }
    public FlowService (BuildRepository buildRepository){
        this.buildRepository = buildRepository;
    }

    public Optional<Build> getLastBuildIssues(){
        return buildRepository.findFirstByOrderByStartAtDesc();
    }

    public Response getLastBuild() throws IOException {
        var b = buildRepository.findFirstByOrderByStartAtDesc();
        if (b.isEmpty()) {
            return Response.builder().build();
        }
        log.info("{}", b.get().getIssuesJson());
        var i = JsonUtil.deserialize(b.get().getIssuesJson(), Issue[].class);
        var iSuccess = Arrays.stream(i).filter(ru.timebook.bro.flow.modules.taskTracker.Issue::isMergeLocalSuccess).count();
        var iFails = i.length - iSuccess;
        return Response.builder()
                .issues(i)
                .issuesSuccess(Math.toIntExact(iSuccess))
                .issuesFails(Math.toIntExact(iFails))
                .buildStartAt(DateTimeUtil.formatFull(b.get().getStartAt()))
                .build();
    }
}
