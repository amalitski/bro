package ru.timebook.bro.flow.modules.taskTracker;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.User;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.configs.Config;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class JiraTaskTracker implements TaskTracker {
    private final Config.TaskTrackers.Jira config;
    private JiraRestClient client;

    public JiraTaskTracker(Config config) {
        this.config = config.getTaskTrackers().getJira();
    }

    @Override
    public List<Issue> getForMerge() {
        var listIssues = new ArrayList<Issue>();
        try {
            listIssues = getIssues();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Exception ", e);
        }
        log.debug("Issues for merge: {}", listIssues.size());
        return listIssues;
    }

    @Override
    public void setDeployed(Issue issue) {
        var client = getClient().getIssueClient();
        try {
            var i = client.getIssue(issue.getId()).get();
            var labels = Set.copyOf(i.getLabels()).stream()
                    .filter(l-> l.equals(config.getIssues().getLabelDeploymentSuccessful()) || l.equals(config.getIssues().getLabelDeploymentFailed()))
                    .collect(Collectors.toSet());
            if (issue.isMergeLocalSuccess()) {
                labels.add(config.getIssues().getLabelDeploymentSuccessful());
            } else {
                labels.add(config.getIssues().getLabelDeploymentFailed());
            }
            if (i.getLabels().size() != labels.size() || !i.getLabels().containsAll(labels)) {
                var ib = new IssueInputBuilder();
                ib.setFieldValue(IssueFieldId.LABELS_FIELD.id, labels);
                getClient().getIssueClient().updateIssue(issue.getId(), ib.build()).get();
                log.trace("Label updated: {} - {}", issue.getId(), labels);
                issue.setIssueUpdated(true);
            }
        } catch (ExecutionException | InterruptedException e) {
            log.error("Exception: ", e);
        }
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    private synchronized JiraRestClient getClient() {
        if (client == null) {
            client = new AsynchronousJiraRestClientFactory()
                    .createWithBasicHttpAuthentication(URI.create(config.getHost()), config.getUsername(), config.getApiToken());
        }
        return client;
    }

    private ArrayList<Issue> getIssues() throws ExecutionException, InterruptedException {
        var listIssues = new ArrayList<Issue>();
        var r = getClient().getSearchClient().searchJql(config.getIssues().getMergeJQL()).get();
        r.getIssues().forEach(i -> {
            var prs = StreamSupport.stream(i.getFields().spliterator(), true)
                    .filter(f -> f.getName().startsWith("Pull Request ") && f.getValue() != null)
                    .map(f -> Issue.PullRequest.builder().uri(f.getValue().toString()).name(f.getName()).build())
                    .toList();
            var reporter = Objects.requireNonNull(i.getReporter());
            var a = Issue.Author.builder()
                    .id(reporter.getAccountId())
                    .avatarUri(Objects.requireNonNull(reporter.getAvatarUri(User.S48_48)).toString())
                    .profileUri(String.format("%s/jira/people/%s", config.getHost(), reporter.getAccountId()))
                    .visibleName(reporter.getDisplayName())
                    .build();
            var issue = Issue.builder()
                    .id(i.getKey()).subject(i.getSummary())
                    .uri(String.format("%s/browse/%s", config.getHost(), i.getKey()))
                    .author(a)
                    .pullRequests(prs)
                    .taskTrackerClassName(this.getClass().getName())
                    .build();
            listIssues.add(issue);
        });
        return listIssues;
    }
}
