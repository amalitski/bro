package ru.timebook.bro.flow.modules.taskTracker;

import java.util.List;

public interface TaskTracker {
    public List<Issue> getForMerge();
    public boolean isEnabled();
    public void setDeployed(List<Issue> issues);
}
