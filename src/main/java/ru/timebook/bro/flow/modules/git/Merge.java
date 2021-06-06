package ru.timebook.bro.flow.modules.git;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@Builder
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

    @Data
    @Builder
    public static class Push {
        private boolean pushed;
        private String log;
    }

    @Data
    @Builder
    public static class Branch {
        private String branchName;
        private boolean mergeLocal;
        private boolean mergeLocalSuccess;
        private String stdout;
        private String stderr;
        private String code;
    }
}
