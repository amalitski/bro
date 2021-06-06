package ru.timebook.bro.flow.modules.taskTracker;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.configurations.Configuration;
import ru.timebook.bro.flow.utils.DateTimeUtil;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RedmineTaskTracker implements TaskTracker {
    private final Configuration.TaskTrackers.Redmine rmConfig;
    private final static Logger logger = LoggerFactory.getLogger(RedmineTaskTracker.class);
    private final RedmineManager manager;

    public RedmineTaskTracker(Configuration configuration) {
        this.rmConfig = configuration.getTaskTrackers().getRedmine();

        this.manager = RedmineManagerFactory
                .createWithApiKey(rmConfig.getHost(), rmConfig.getApiKey());
        this.manager.setObjectsPerPage(100);
    }

    @Override
    public boolean isEnabled(){
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
            logger.error("Redmine error", e);
        }
        logger.debug("Issues for merge: {}", listIssues.size());
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
            var row = Issue.builder()
                    .id(String.valueOf(i.getId()))
                    .subject(i.getSubject())
                    .taskTracker(this)
                    .taskTrackerClazz(this.getClass())
                    ;
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

