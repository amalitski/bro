package ru.timebook.bro.flow.modules.taskTracker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import ru.timebook.bro.flow.modules.git.Merge;

import java.util.List;

@Data
@Builder
public class Issue {
    private String id;
    private String subject;
    private String statusId;
    private String priorityId;
    private String trackerId;

//    public boolean isMergeLocalSuccess(){
//        return this.pullRequests.stream().anyMatch(p -> p.getBranch() != null && !p.getBranch().isMergeLocalSuccess());
//    }

    @Singular
    private List<PullRequest> pullRequests;

    @JsonIgnore
    private TaskTracker taskTracker;
    @JsonIgnore
    private Class<? extends TaskTracker> taskTrackerClazz;

    @Data
    @Builder
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

