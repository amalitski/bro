package ru.timebook.bro.flow.modules.git;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.ProxyClientConfig;
import org.gitlab4j.api.models.PipelineFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.modules.taskTracker.Issue;
import ru.timebook.bro.flow.utils.BufferUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GitlabGitRepository implements GitRepository {
    private final Config.Repositories.Gitlab config;
    private final Config.Stage configStage;
    private GitLabApi gitLabApi;

    public GitlabGitRepository(Config config) {
        this.config = config.getRepositories().getGitlab();
        this.configStage = config.getStage();
    }

    @Override
    public void getInfo(List<Issue> issues) {
        issues.parallelStream().forEach(i -> {
            i.getPullRequests().forEach(this::getPullRequestInfo);
        });
        log.trace("Issues information loaded");
    }

    public void getPullRequestInfo(Issue.PullRequest pullRequest) {
        if (pullRequest.getUri().trim().length() <= 8) {
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
            log.error("Catch exception", e);
        }
    }

    public GitLabApi getApi() {
        if (gitLabApi == null) {
            if (!config.getProxy().isEmpty()) {
                Map<String, Object> proxyConfig = ProxyClientConfig.createProxyClientConfig(config.getProxy());
                gitLabApi = new GitLabApi(config.getHost(), config.getToken(), null, proxyConfig);
            } else {
                gitLabApi = new GitLabApi(config.getHost(), config.getToken());
            }
            gitLabApi.setRequestTimeout(config.getTimeout(), config.getTimeout());
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

    public String getCommitterAvatarUri(String email) {
        return BufferUtil.key(email, () -> {
            log.trace("Request avatar_uri: {}", email);
            RestTemplate restTemplate = new RestTemplate();
            var result = restTemplate
                    .getForObject(String.format("%s/api/v4/avatar?email=%s&size=50", config.getHost(), email), HashMap.class);
            assert result != null;
            if (result.containsKey("avatar_url")) {
                return result.get("avatar_url").toString();
            }
            return "";
        });
    }

    public List<Merge> getMerge(List<Issue> issues) {
        var map = new HashMap<String, Merge>();
        for (var i : issues) {
            for (var pr : i.getPullRequests()) {
                if (pr.getSourceBranchName() == null) {
                    continue;
                }
                pr.setGitRepositoryClazz(this);
                Merge merge;
                if (map.containsKey(pr.getProjectName())) {
                    merge = map.get(pr.getProjectName());
                    merge.getBranches().add(Merge.Branch.builder()
                            .branchName(pr.getSourceBranchName())
                            .targetBranchName(pr.getTargetBranchName())
                            .merged(pr.getMerged())
                            .build());
                    map.remove(pr.getProjectName());
                } else {
                    merge = getMergeByPr(pr);
                }
                map.put(pr.getProjectName(), merge);
            }
        }
        var maps = new ArrayList<>(map.values());
        config.getRepositories().forEach(r -> {
            if (maps.stream().noneMatch(m -> m.getProjectName().equals(r.getPath()))) {
                maps.add(getMergeByRepo(r));
            }
        });
        log.debug("Merge count: {}", maps.size());
        return maps;
    }

    private Merge getMergeByPr(Issue.PullRequest pr) {
        var branches = new LinkedHashSet<String>();
        var rep = getPreMergeBranch(pr.getProjectName());
        rep.ifPresent(repository -> branches.addAll(repository.getPreMerge()));
        branches.add(pr.getSourceBranchName());
        return Merge.builder()
                .branches(branches.stream().map(v -> Merge.Branch.builder()
                        .branchName(v)
                        .targetBranchName(pr.getTargetBranchName())
                        .merged(pr.getMerged()).build()).collect(Collectors.toList()))
                .projectId(DigestUtils.md5DigestAsHex(pr.getProjectName().getBytes(StandardCharsets.UTF_8)))
                .projectName(pr.getProjectName())
                .projectSafeName(pr.getProjectName().replaceAll("[^A-Za-z0-9\\-_.]", "."))
                .projectShortName(pr.getProjectName().substring(0, 1).toUpperCase())
                .httpUrlRepo(pr.getHttpUrlRepo())
                .sshUrlRepo(pr.getSshUrlRepo())
                .push(Merge.Push.builder().deploy(Merge.Push.Deploy.builder().build()).build())
                .build();
    }

    private Merge getMergeByRepo(Config.Repositories.Gitlab.Repository repo) {
        var merge = Merge.builder();
        try {
            var project = getApi().getProjectApi().getProject(repo.getPath());
            merge
                    .branches(repo.getPreMerge().stream().map(v -> Merge.Branch.builder()
                            .branchName(v)
                            .targetBranchName(v)
                            .merged(true).build()).collect(Collectors.toList()))
                    .projectId(DigestUtils.md5DigestAsHex(repo.getPath().getBytes(StandardCharsets.UTF_8)))
                    .projectName(repo.getPath())
                    .projectSafeName(repo.getPath().replaceAll("[^A-Za-z0-9\\-_.]", "."))
                    .projectShortName(repo.getPath().substring(0, 1).toUpperCase())
                    .httpUrlRepo(project.getHttpUrlToRepo())
                    .sshUrlRepo(project.getSshUrlToRepo())
                    .push(Merge.Push.builder().deploy(Merge.Push.Deploy.builder().build()).build());
        } catch (Exception e) {
            log.error("Catch exception", e);
        }
        return merge.build();
    }

    private Optional<Config.Repositories.Gitlab.Repository> getPreMergeBranch(String projectName) {
        if (config.getRepositories() == null) {
            return Optional.empty();
        }
        return config.getRepositories().stream().filter(r -> r.getPath().equals(projectName)).findFirst();
    }

    public Optional<String> getJobStatus(String projectName, Integer jobId) {
        var api = getApi();
        try {
            var j = api.getJobApi().getJob(projectName, jobId);
            return Optional.of(j.getStatus().name().toLowerCase());
        } catch (GitLabApiException e) {
            log.error("Find job return Exception", e);
        }
        return Optional.empty();
    }

    public Merge.Push.Deploy getDeploy(String projectName, String ref) {
        var api = getApi();
        var p = new PipelineFilter();
        p.setRef(ref);
        try {
            return api.getPipelineApi().getPipelines(projectName, p).stream().map(pp -> {
                try {
                    var job = api.getJobApi().getJobsForPipeline(projectName, pp.getId()).stream()
                            .filter(jj -> jj.getName().equals(configStage.getDeploy().getJobName())).findFirst();
                    if (job.isPresent()) {
                        var j = job.get();
                        return Merge.Push.Deploy.builder()
                                .commitSha(j.getCommit().getId())
                                .jobId(j.getId())
                                .jobStatus(j.getStatus().name().toLowerCase())
                                .pipelineId(j.getPipeline().getId())
                                .pipelineUri(j.getPipeline().getWebUrl()).build();
                    }
                } catch (GitLabApiException e) {
                    log.error("Find job for deploy to stage return Exception", e);
                }
                return null;
            }).filter(Objects::nonNull).findFirst().orElse(Merge.Push.Deploy.builder().build());
        } catch (GitLabApiException e) {
            log.error("Find job for deploy to stage return Exception", e);
        }
        return Merge.Push.Deploy.builder().build();
    }
}

