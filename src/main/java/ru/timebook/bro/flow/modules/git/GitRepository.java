package ru.timebook.bro.flow.modules.git;

import ru.timebook.bro.flow.modules.taskTracker.Issue;

import java.util.List;

public interface GitRepository {
    public void getInfo(List<Issue> issues);

    public List<Merge> getMerge(List<Issue> issues);

    public String getCommitterAvatarUri(String email);

    public boolean isEnabled();
}
