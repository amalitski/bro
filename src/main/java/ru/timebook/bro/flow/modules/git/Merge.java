package ru.timebook.bro.flow.modules.git;

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
    private String projectName;
    private List<Branch> branches;
    private File dirMerge;
    private File dirRepo;
    private String checkSum;
    private String log;
    private Push push;
    private String initStdout;
    private String initCode;

    private String getProjectShortName(){
        return this.projectName.substring(0,1).toUpperCase();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Push {
        private boolean pushed;
        private String log;
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
            private String committerDate;
            private String committerAvatarUri;
            private String subject;
        }
    }
}
