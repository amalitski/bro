package ru.timebook.bro.flow.modules.git;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.ProxyClientConfig;
import org.gitlab4j.api.models.Job;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.PipelineFilter;
import org.gitlab4j.api.models.Project;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;
import ru.timebook.bro.flow.exceptions.FlowRuntimeException;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.modules.taskTracker.Issue;
import ru.timebook.bro.flow.services.CacheService;
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
    private CacheService cacheService;

    public GitlabGitRepository(Config config, CacheService cacheService) {
        this.config = config.getRepositories().getGitlab();
        this.configStage = config.getStage();
        this.cacheService = cacheService;
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
        try {
            var prInfo = getPullRequestInfo(pullRequest.getUri());
            var mergeRequestId = Integer.parseInt(prInfo.get("id"));
            var projectName = prInfo.get("project");
            var project = getProject(projectName);
            var mergeRequest = getMergeRequest(projectName, mergeRequestId);
            pullRequest.setProjectName(projectName);
            pullRequest.setHttpUrlRepo(project.getHttpUrlToRepo());
            pullRequest.setSshUrlRepo(project.getSshUrlToRepo());
            pullRequest.setSourceBranchName(mergeRequest.getSourceBranch());
            pullRequest.setTargetBranchName(mergeRequest.getTargetBranch());
            pullRequest.setMerged(mergeRequest.getState().equals("merged"));
        } catch (IllegalArgumentException e) {
            pullRequest.setIncorrectUri(true);
            log.warn("Incorrect uri", e);
        }
    }

    public GitLabApi getApi() {
        if (gitLabApi == null) {
            synchronized (GitlabGitRepository.class) {
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
        }
        return gitLabApi;
    }

    private HashMap<String, String> getPullRequestInfo(String prUri) throws IllegalArgumentException {
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
        throw new IllegalArgumentException("Project name not found in uri: " + prUri);
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
                .needForcePush(true)
                .build();
    }

    private Merge getMergeByRepo(Config.Repositories.Gitlab.Repository repo) throws FlowRuntimeException {
        var merge = Merge.builder();
        var project = getProject(repo.getPath());
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
        return merge.build();
    }

    private Optional<Config.Repositories.Gitlab.Repository> getPreMergeBranch(String projectName) {
        if (config.getRepositories() == null) {
            return Optional.empty();
        }
        return config.getRepositories().stream().filter(r -> r.getPath().equals(projectName)).findFirst();
    }

    public Optional<String> getJobStatus(String projectName, Integer jobId) throws FlowRuntimeException {
        var api = getApi();
        try {
            var j = api.getJobApi().getJob(projectName, jobId);
            return Optional.of(j.getStatus().name().toLowerCase());
        } catch (GitLabApiException e) {
            throw new FlowRuntimeException(e);
        }
    }

    public Merge.Push.Deploy getDeploy(String projectName, String ref) throws FlowRuntimeException {
        var api = getApi();
        var p = new PipelineFilter();
        p.setRef(ref);
        try {
            return api.getPipelineApi().getPipelines(projectName, p).stream().map(pp -> {
                var job = getDeployJobByPipeline(projectName, pp.getId());
                return job.map(value -> Merge.Push.Deploy.builder()
                        .commitSha(value.getCommit().getId())
                        .jobId(value.getId())
                        .jobStatus(value.getStatus().name().toLowerCase())
                        .pipelineId(value.getPipeline().getId())
                        .pipelineUri(value.getPipeline().getWebUrl()).build()).orElse(null);
            }).filter(Objects::nonNull).findFirst().orElse(Merge.Push.Deploy.builder().build());
        } catch (GitLabApiException e) {
            log.error("Find job for deploy to stage ({} {}) return Exception", projectName, ref);
            throw new FlowRuntimeException(e);
        }
    }

    private Optional<Job> getDeployJobByPipeline(String projectName, Integer pipelineId) throws FlowRuntimeException {
        try {
            return getApi().getJobApi().getJobsForPipeline(projectName, pipelineId)
                    .stream()
                    .filter(jj -> jj.getName().equals(configStage.getDeploy().getJobName())).findFirst();
        } catch (GitLabApiException e) {
            log.error("Request to Job ({} {}) return exception", projectName, pipelineId);
            throw new FlowRuntimeException(e);
        }
    }

    private Project getProject(String projectName) throws FlowRuntimeException {
        try {
            var pr = (Optional<Project>) cacheService.get(projectName);
            if (pr.isEmpty()) {
                var p = getApi().getProjectApi().getProject(projectName);
                cacheService.set(projectName, p, 60*60);
                return p;
            } else {
                log.warn("FROM cache: {}", projectName);
            }
            return pr.get();
        } catch (GitLabApiException e) {
            log.error("Request to Project ({}) return exception", projectName);
            throw new FlowRuntimeException(e);
        }
    }

    private MergeRequest getMergeRequest(String projectName, Integer mrId) throws FlowRuntimeException {
        try {
            return getApi().getMergeRequestApi().getMergeRequest(projectName, mrId);
        } catch (GitLabApiException e) {
            log.error("Request to Merge ({} {}) return exception", projectName, mrId);
            throw new FlowRuntimeException(e);
        }
    }
}