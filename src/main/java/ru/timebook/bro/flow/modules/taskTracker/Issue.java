package ru.timebook.bro.flow.modules.taskTracker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import ru.timebook.bro.flow.modules.git.Merge;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Issue {
    private String id;
    private String subject;
    private String statusId;
    private String priorityId;
    private String trackerId;
    private String uri;
    private Author author;

    public boolean isMergeLocalSuccess(){
        return this.pullRequests.stream().filter(p -> p.getBranch() != null).allMatch(p -> p.getBranch().isMergeLocalSuccess());
    }

    @Singular
    private List<PullRequest> pullRequests;

    @JsonIgnore
    private TaskTracker taskTracker;
    @JsonIgnore
    private Class<? extends TaskTracker> taskTrackerClazz;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Author {
        private String id;
        private String avatarUri;
        private String profileUri;
        private String visibleName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PullRequest {
        private String uri;
        private String name;

        private Boolean merged;
        private String sourceBranchName;
        private String targetBranchName;
        private String projectName;
        private String httpUrlRepo;
        private String sshUrlRepo;
        private Merge.Branch branch;
    }
}

