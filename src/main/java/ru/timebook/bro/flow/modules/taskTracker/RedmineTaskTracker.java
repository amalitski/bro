package ru.timebook.bro.flow.modules.taskTracker;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.utils.DateTimeUtil;
import ru.timebook.bro.flow.utils.GravatarUtil;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RedmineTaskTracker implements TaskTracker {
    private final Config.TaskTrackers.Redmine config;
    private final DateTimeUtil dateTimeUtil;
    private final GravatarUtil gravatarUtil;
    private RedmineManager api;

    public RedmineTaskTracker(Config config, DateTimeUtil dateTimeUtil, GravatarUtil gravatarUtil) {
        this.config = config.getTaskTrackers().getRedmine();
        this.dateTimeUtil = dateTimeUtil;
        this.gravatarUtil = gravatarUtil;
    }

    public RedmineManager getApi() {
        if (this.api == null) {
            var cxMgr = new PoolingHttpClientConnectionManager();
            cxMgr.setMaxTotal(100);
            cxMgr.setDefaultMaxPerRoute(20);
            var client = HttpClients.custom()
                    .setConnectionManager(cxMgr)
                    .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(config.getTimeout()).build())
                    .build();
            this.api = RedmineManagerFactory.createWithApiKey(config.getHost(), config.getApiKey(), client);
            this.api.setObjectsPerPage(100);
        }
        return this.api;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public List<Issue> getForMerge() {
        var listIssues = new ArrayList<Issue>();
        try {
            for (var t : config.getTrackers()) {
                for (var s : config.getStatuses()) {
                    if (s.isNeedMerge()) {
                        var i = getIssues(s.getId(), t.getId());
                        listIssues.addAll(i);
                    }
                }
            }
        } catch (RedmineException e) {
            log.error("Redmine error", e);
        }
        listIssues.sort(Comparator.comparing(Issue::getId).reversed());
        log.debug("Issues for merge: {}", listIssues.size());
        return listIssues;
    }

    @Override
    public void setDeployed(Issue issue) {
        // todo
    }

    private List<Issue> getIssues(String statusId, String trackerId) throws RedmineException {
        var params = new HashMap<String, String>();
        params.put("status_id", statusId);
        params.put("tracker_id", trackerId);
        params.put("sort", "id:desc");

        var customFieldsIds = getCustomFieldsIds();
        var duration = Duration.parse(config.getAfterUpdateTime());
        var afterDate = LocalDate.now().plusDays(duration.toDays());

        var listIssues = new ArrayList<Issue>();
        var issues = getApi().getIssueManager().getIssues(params);

        issues.parallelStream().forEach(i -> {
            if (dateTimeUtil.toLocalDate(i.getUpdatedOn()).isBefore(afterDate)) {
                return;
            }
            try {
                var aFull = getApi().getUserManager().getUserById(i.getAuthor().getId());
                var avatar = (aFull.getMail() != null) ? gravatarUtil.getUri(aFull.getMail(), 50) : null;
                var a = Issue.Author.builder()
                        .id(String.valueOf(i.getAuthor().getId()))
                        .avatarUri(avatar)
                        .profileUri(String.format("%s/users/%s", config.getHost(), i.getAuthor().getId()))
                        .visibleName(String.format("%s", i.getAuthor().getFullName()))
                        .build();
                var row = Issue.builder()
                        .id(String.valueOf(i.getId()))
                        .uri(String.format("%s/issues/%s", config.getHost(), i.getId()))
                        .subject(i.getSubject())
                        .author(a)
                        .taskTrackerClassName(this.getClass().getName());
                for (var f : i.getCustomFields()) {
                    if (customFieldsIds.contains(String.valueOf(f.getId()))) {
                        var pr = Issue.PullRequest.builder().uri(f.getValue()).name(f.getName()).build();
                        row.pullRequest(pr);
                    }
                }
                listIssues.add(row.build());
            } catch (RedmineException e) {
                log.error("Redmine catch exception", e);
            }
        });
        return listIssues;
    }

    private List<String> getCustomFieldsIds() {
        return config.getCustomFields().stream()
                .map(Config.TaskTrackers.Redmine.CustomField::getId).collect(Collectors.toList());
    }
}

