package ru.timebook.bro.flow.modules.git;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.io.File;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Merge {
    private String httpUrlRepo;
    private String sshUrlRepo;
    private String projectId;
    private String projectName;
    private String projectSafeName;
    private String projectShortName;
    private List<Branch> branches;
    private File dirMerge;
    private File dirRepo;
    private String checkSum;
    private String lastCommitSha;
    private String log;
    private Push push;
    private String initStdout;
    private String initCode;

    @JsonIgnore
    public boolean getInitSuccess() {
        return this.initCode.equals("0");
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Push {
        private boolean pushed;
        private String log;
        private Deploy deploy;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Deploy {
            private String commitSha;
            private Integer jobId;
            private String jobStatus;
            private Integer pipelineId;
            private String pipelineUri;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Branch {
        private String branchName;
        private String targetBranchName;
        private boolean mergeLocal;
        private boolean mergeLocalSuccess;
        private boolean merged;
        private String stdout;
        private String stderr;
        private String code;
        private List<Commit> commits;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Commit {
            private String hash;
            private String committerEmail;
            private String committerName;
            private String committerDate;
            private String committerAvatarUri;
            private String subject;
        }
    }
}
