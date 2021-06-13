package ru.timebook.bro.flow.modules.taskTracker;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.configurations.Configuration;
import ru.timebook.bro.flow.utils.DateTimeUtil;
import ru.timebook.bro.flow.utils.GravatarUtil;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RedmineTaskTracker implements TaskTracker {
    private final Configuration.TaskTrackers.Redmine rmConfig;
    private final RedmineManager manager;

    public RedmineTaskTracker(Configuration configuration) {
        this.rmConfig = configuration.getTaskTrackers().getRedmine();

        this.manager = RedmineManagerFactory
                .createWithApiKey(rmConfig.getHost(), rmConfig.getApiKey());
        this.manager.setObjectsPerPage(100);
    }

    @Override
    public boolean isEnabled() {
        return rmConfig.isEnabled();
    }

    @Override
    public List<Issue> getForMerge() {
        var listIssues = new ArrayList<Issue>();
        try {
            for (var t : rmConfig.getTrackers()) {
                for (var s : rmConfig.getStatuses()) {
                    if (s.isNeedMerge()) {
                        var i = getIssues(s.getId(), t.getId());
                        listIssues.addAll(i);
                    }
                }
            }
        } catch (RedmineException e) {
            log.error("Redmine error", e);
        }
        log.debug("Issues for merge: {}", listIssues.size());
        return listIssues;
    }

    @Override
    public void setDeployed(List<Issue> issues) {
        // todo
    }

    private List<Issue> getIssues(String statusId, String trackerId) throws RedmineException {
        var params = new HashMap<String, String>();
        params.put("status_id", statusId);
        params.put("tracker_id", trackerId);
        params.put("sort", "updated_on:desc");

        var customFieldsIds = getCustomFieldsIds();
        var duration = Duration.parse(rmConfig.getAfterUpdateTime());
        var afterDate = LocalDate.now().plusDays(duration.toDays());

        var listIssues = new ArrayList<Issue>();
        var issues = manager.getIssueManager().getIssues(params);

        for (var i : issues) {
            if (DateTimeUtil.toLocalDate(i.getUpdatedOn()).isBefore(afterDate)) {
                continue;
            }

            // i.getAuthor() doesnt loaded email address
            var aFull = manager.getUserManager().getUserById(i.getAuthor().getId());
            var avatar = (aFull.getMail() != null) ? GravatarUtil.getUri(aFull.getMail(), 50) : null;
            var a = Issue.Author.builder()
                    .id(String.valueOf(i.getAuthor().getId()))
                    .avatarUri(avatar)
                    .profileUri(String.format("%s/users/%s", rmConfig.getHost(), i.getAuthor().getId()))
                    .visibleName(String.format("%s", i.getAuthor().getFullName()))
                    .build();

            var row = Issue.builder()
                    .id(String.valueOf(i.getId()))
                    .uri(String.format("%s/issues/%s", rmConfig.getHost(), i.getId()))
                    .subject(i.getSubject())
                    .taskTracker(this)
                    .author(a)
                    .taskTrackerClazz(this.getClass());

            for (var f : i.getCustomFields()) {
                if (customFieldsIds.contains(String.valueOf(f.getId()))) {
                    var pr = Issue.PullRequest.builder().uri(f.getValue()).name(f.getName()).build();
                    row.pullRequest(pr);
                }
            }
            listIssues.add(row.build());
        }
        return listIssues;
    }

    private List<String> getCustomFieldsIds() {
        return rmConfig.getCustomFields().stream()
                .map(Configuration.TaskTrackers.Redmine.CustomField::getId).collect(Collectors.toList());
    }
}

