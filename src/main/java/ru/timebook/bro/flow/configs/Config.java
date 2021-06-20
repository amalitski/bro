package ru.timebook.bro.flow.configs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Data
@Component
@EnableScheduling
@ConfigurationProperties("bro.flow")
public class Config {
    private Stage stage;
    private TaskTrackers taskTrackers;
    private Repositories repositories;

    @Data
    public static class Stage {
        private String name;
        private String uri;
        private String branchName;
        private String pushCmd;
        private String cronReceive;
        private String basePath;
        private Git git;
        private Temp temp;

        @Data
        public static class Temp {
            private String tempDir;
            private String initDir;
            private String cleanAfter;
        }

        @Data
        public static class Git {
            private String userName;
            private String userEmail;
        }
    }

    @Data
    public static class TaskTrackers {
        private Redmine redmine;
        private Jira jira;

        @Data
        public static class Redmine {
            private String host;
            private String apiKey;
            private String afterUpdateTime;
            private List<Status> statuses;
            private List<Tracker> trackers;
            private List<CustomField> customFields;
            private int timeout;
            private boolean enabled;

            @Data
            public static class Tracker {
                private String id;
            }

            @Data
            public static class Status {
                private String id;
                private boolean needMerge;
            }

            @Data
            public static class CustomField {
                private String id;
                private String value;
            }
        }

        @Data
        public static class Jira {
            private String host;
            private String username;
            private String apiToken;
            private boolean enabled;
            private Issues issues;

            @Data
            public static class Issues {
                private String mergeJQL;
                private String labelDeployed;
            }
        }
    }

    @Data
    public static class Repositories {
        private Gitlab gitlab;

        @Data
        public static class Gitlab {
            private String host;
            private String token;
            private String proxy;
            private String tempDir;
            private boolean enableRequestLogging;
            private Stage stage;
            private List<Repository> repositories;
            private int timeout;
            private boolean enabled;

            @Data
            public static class Repository {
                private String path;
                private LinkedHashSet<String> preMerge;
            }

            @Data
            public static class Stage {
                private String branch;
            }
        }
    }
}
