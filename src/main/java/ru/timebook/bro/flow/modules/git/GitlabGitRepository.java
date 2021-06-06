package ru.timebook.bro.flow.modules.git;

import com.google.common.collect.Lists;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.ProxyClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.configurations.Configuration;
import ru.timebook.bro.flow.modules.taskTracker.Issue;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GitlabGitRepository implements GitRepository {
    private final Configuration.Repositories.Gitlab config;
    private final static Logger logger = LoggerFactory.getLogger(GitlabGitRepository.class);
    private GitLabApi gitLabApi;

    public GitlabGitRepository(Configuration configuration) {
        this.config = configuration.getRepositories().getGitlab();
    }

    @Override
    public void getInfo(List<Issue> issues) {
        issues.forEach(i -> {
            i.getPullRequests().forEach(this::getPullRequestInfo);
        });
        logger.trace("Issues information loaded");
    }

    public void getPullRequestInfo(Issue.PullRequest pullRequest) {
        if (pullRequest.getUri().isEmpty()) {
            return;
        }
        var api = getApi();
        try {
            var prInfo = getPullRequestInfo(pullRequest.getUri());
            var mergeRequestId = Integer.parseInt(prInfo.get("id"));
            var projectName = prInfo.get("project");
            var project = api.getProjectApi().getProject(projectName);
            var mergeRequest = api.getMergeRequestApi().getMergeRequest(projectName, mergeRequestId);

            pullRequest.setProjectName(projectName);
            pullRequest.setHttpUrlRepo(project.getHttpUrlToRepo());
            pullRequest.setSshUrlRepo(project.getSshUrlToRepo());
            pullRequest.setSourceBranchName(mergeRequest.getSourceBranch());
            pullRequest.setTargetBranchName(mergeRequest.getTargetBranch());
            pullRequest.setMerged(mergeRequest.getState().equals("merged"));
        } catch (Exception e) {
            logger.error("Catch exception", e);
        }
    }

    private GitLabApi getApi() {
        if (gitLabApi == null) {
            if (!config.getProxy().isEmpty()) {
                Map<String, Object> proxyConfig = ProxyClientConfig.createProxyClientConfig(config.getProxy());
                gitLabApi = new GitLabApi(config.getHost(), config.getToken(), null, proxyConfig);
            } else {
                gitLabApi = new GitLabApi(config.getHost(), config.getToken());
            }
            if (config.isEnableRequestLogging()) {
                gitLabApi.enableRequestResponseLogging();
            }
        }
        return gitLabApi;
    }

    private HashMap<String, String> getPullRequestInfo(String prUri) throws Exception {
        var map = new HashMap<String, String>();
        var reg = Lists.newArrayList(
                "(?:://)(?<host>[^/]+)/(?<project>.+)/-/merge_requests/(?<id>[0-9]+)",
                "(?:://)(?<host>[^/]+)/(?<project>.+)/merge_requests/(?<id>[0-9]+)"
        );
        for (var r : reg) {
            Pattern p = Pattern.compile(r, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(prUri);
            if (m.find() && m.group("id").length() >= 1 && m.group("project").length() >= 1) {
                map.put("id", m.group("id"));
                map.put("project", m.group("project"));
                return map;
            }
        }
        throw new Exception("Project name not found in uri: " + prUri);
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public List<Merge> getMerge(List<Issue> issues) {
        var map = new HashMap<String, Merge>();
        for (var i : issues) {
            for (var pr : i.getPullRequests()) {
                if (pr.getSourceBranchName() == null) {
                    continue;
                }
                Merge merge;
                if (map.containsKey(pr.getProjectName())) {
                    merge = map.get(pr.getProjectName());
                    merge.getBranches().add(Merge.Branch.builder().branchName(pr.getSourceBranchName()).build());
                    map.remove(pr.getProjectName());
                } else {
                    var branches = new LinkedHashSet<String>();
                    var rep = getPreMergeBranch(pr.getProjectName());
                    rep.ifPresent(repository -> branches.addAll(repository.getPreMerge()));
                    branches.add(pr.getSourceBranchName());
                    merge = Merge.builder()
                            .branches(branches.stream().map(v -> Merge.Branch.builder().branchName(v).build()).collect(Collectors.toList()))
                            .projectName(pr.getProjectName())
                            .httpUrlRepo(pr.getHttpUrlRepo())
                            .sshUrlRepo(pr.getSshUrlRepo())
                            .push(Merge.Push.builder().build())
                            .build();
                }
                map.put(pr.getProjectName(), merge);
            }
        }
        logger.debug("Merge count: {}", map.size());
        return new ArrayList<>(map.values());
    }

    private Optional<Configuration.Repositories.Gitlab.Repository> getPreMergeBranch(String projectName) {
        return config.getRepositories().stream().filter(r -> r.getPath().equals(projectName)).findFirst();
    }
}
