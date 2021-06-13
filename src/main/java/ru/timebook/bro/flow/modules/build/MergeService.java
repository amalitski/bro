package ru.timebook.bro.flow.modules.build;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.configurations.Configuration;
import ru.timebook.bro.flow.modules.taskTracker.Issue;
import ru.timebook.bro.flow.modules.git.Merge;
import ru.timebook.bro.flow.utils.DateTimeUtil;
import ru.timebook.bro.flow.utils.StringUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MergeService {
    private final Configuration configuration;
    private final ProjectRepository projectRepository;

    public MergeService(Configuration configuration, ProjectRepository projectRepository) {
        this.configuration = configuration;
        this.projectRepository = projectRepository;
    }

    public void merge(List<Merge> merges) {
        merges.parallelStream().forEach(merge -> {
            try {
                initRepo(merge);
                mergeRepo(merge);
            } catch (Exception e) {
                log.error("Merge exception", e);
            }
        });
    }

    public void push(List<Merge> merges) throws Exception {
        for (var merge : merges) {
            pushExec(merge);
        }
    }

    private void pushExec(Merge merge) throws Exception {
        if (configuration.getStage().getBranchName().isEmpty() || configuration.getStage().getPushCmd().isEmpty()) {
            throw new Exception("Invalid configuration: bro.flow.stage.branchName or bro.flow.stage.pushCmd empty!");
        }
        var project = projectRepository.findByName(merge.getProjectName());
        if (project.isPresent() && project.get().getBuildCheckSum().equals(merge.getCheckSum())) {
            return;
        }

        var cmd = configuration.getStage().getPushCmd();
        var resp = exec(cmd, merge.getDirRepo());
        merge.getPush().setLog(resp.get("stdout"));
        merge.getPush().setPushed(resp.get("code").equals("0"));
        if (merge.getPush().isPushed()) {
            log.debug("Pushed project {}:{}", merge.getProjectName(), configuration.getStage().getBranchName());
        }
    }

    public static Optional<Merge.Branch> getBranchByPr(Issue.PullRequest pr, List<Merge> merges) {
        if (pr.getSourceBranchName() == null) {
            return Optional.empty();
        }
        var mr = merges.stream().filter(m -> m.getSshUrlRepo().equals(pr.getSshUrlRepo())).findFirst();
        if (mr.isEmpty()) {
            return Optional.empty();
        }
        return mr.get().getBranches().stream().filter(b -> b.getBranchName().equals(pr.getSourceBranchName())).findFirst();
    }

    public static void updateCommitters(Issue issue) {
        var committers = new HashSet<Issue.Committer>();
        issue.getPullRequests().forEach(pr -> {
            if (pr.getBranch() != null && pr.getBranch().getCommits() != null) {
                pr.getBranch().getCommits().forEach(c -> {
                    var repo = pr.getGitRepositoryClazz();
                    c.setCommitterAvatarUri(repo.getCommitterAvatarUri(c.getCommitterEmail()));
                    var committer = Issue.Committer.builder().avatarUri(c.getCommitterAvatarUri()).build();
                    committers.add(committer);
                });
            }
        });
        issue.setCommitters(committers.stream().toList());

    }

    private void initRepo(Merge merge) throws Exception {
        var dirname = getInitDirPath(merge.getProjectName());
        var dir = new File(dirname);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("Failed to create init directory: " + dirname);
        }
        var f = dir.listFiles();
        if (f != null && f.length == 0) {
            var resp = exec("git clone " + merge.getSshUrlRepo() + " ./", dir);
            if (!resp.get("code").equals("0")) {
                merge.setInitStdout(resp.get("stdout"));
                merge.setInitCode(resp.get("code"));
                throw new Exception(String.format("Clone %s repository with error. See logs for detail information. ", merge.getSshUrlRepo()));
            }
        }
    }

    private void mergeRepo(Merge merge) throws Exception {
        var dirname = getMergeDirPath(merge);
        var dirMerge = new File(dirname);
        var dirRepo = new File(dirname + File.separator + "repo");
        var dirInit = new File(getInitDirPath(merge.getProjectName()));
        if (!dirMerge.exists() && !dirMerge.mkdirs()) {
            throw new Exception("Failed to create merge directory: " + dirname);
        } else if (!dirRepo.mkdirs()) {
            throw new Exception("Failed to create repo directory: " + dirRepo.getPath());
        } else if (!exec("git fetch origin", dirInit).get("code").equals("0")) {
            throw new Exception("Failed to fetch origin: " + dirInit.getPath());
        }
        merge.setDirRepo(dirRepo);
        merge.setDirMerge(dirMerge);
        FileUtils.copyDirectory(dirInit, dirRepo);
        mergeRecursive(merge, dirRepo);

        var log = exec("git log -25 --pretty=format:'%cd \\t %an : %s'", dirRepo);
        merge.setLog(log.get("stdout"));
        var checkSum = exec("git log -1000 --pretty=format:\"%s\" | md5sum | awk '{print $1}'", dirRepo);
        merge.setCheckSum(checkSum.get("stdout"));
    }

    private boolean mergeRecursive(Merge merge, File dirRepo) throws InterruptedException, IOException {
        var branchFirst = merge.getBranches().stream().findFirst().get();
        var resetResp = exec("git reset --hard origin/" + branchFirst.getBranchName(), dirRepo);
        if (!resetResp.get("code").equals("0")) {
            merge.setInitStdout(resetResp.get("stdout"));
            merge.setInitCode(resetResp.get("code"));
            return false;
        }
        var chResp = exec("git checkout -f origin/" + branchFirst.getBranchName(), dirRepo);
        if (!chResp.get("code").equals("0")) {
            merge.setInitStdout(chResp.get("stdout"));
            merge.setInitCode(chResp.get("code"));
            return false;
        }

        for (var branch : merge.getBranches()) {
            if (branch.isMergeLocal() && !branch.isMergeLocalSuccess()) {
                log.trace("Skip branch `{}`. Merge with error.", branch.getBranchName());
                continue;
            }
            branch.setCommits(getCommits(branch, dirRepo));

            var msg = "Merge branch '" + branch.getBranchName() + "' into stage '" + configuration.getStage().getBranchName() + "'";
            var resp = exec("git merge -m \"" + msg + "\" origin/" + branch.getBranchName(), dirRepo);
            var success = resp.get("code").equals("0");
            branch.setStdout(resp.get("stdout"));
            branch.setStderr(resp.get("stderr"));
            branch.setCode(resp.get("code"));
            branch.setMergeLocalSuccess(success);
            branch.setMergeLocal(true);
            if (!success) {
                return mergeRecursive(merge, dirRepo);
            }
        }
        return true;
    }

    private static List<Merge.Branch.Commit> getCommits(Merge.Branch branch, File dirRepo) throws IOException, InterruptedException {
        var commits = new ArrayList<Merge.Branch.Commit>();
        var cmdLog = MessageFormat.format("git log --pretty=format:\"%H|||%ce|||%ci|||%f\" origin/{1}..origin/{0}", branch.getBranchName(), branch.getTargetBranchName());
        var respLog = exec(cmdLog, dirRepo);
        var data = respLog.get("stdout").trim();
        if (data.isEmpty()) {
            return commits;
        }
        Arrays.stream(data.split("\n")).forEach(row -> {
            var parts = row.trim().split(Pattern.quote("|||"));
            if (!parts[0].isEmpty() && !parts[1].isEmpty() && !parts[2].isEmpty() && parts.length > 3) {
                commits.add(Merge.Branch.Commit.builder()
                        .hash(parts[0].trim())
                        .committerEmail(parts[1].trim())
                        .committerDate(parts[2].trim())
                        .subject(parts[3].trim())
                        .build());
            }
        });
        return commits;
    }

    private static HashMap<String, String> exec(String cmd, File workdir) throws InterruptedException, IOException {
        var timer = Stopwatch.createStarted();
        ProcessBuilder builder = new ProcessBuilder();
        builder.directory(workdir);
        builder.command("bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        BufferedReader output = getOutput(process);
        BufferedReader error = getError(process);

        String line = "";
        var outStr = new StringBuilder();
        while ((line = output.readLine()) != null) {
            outStr.append(line);
            outStr.append(System.lineSeparator());
        }
        var errStr = new StringBuilder();
        while ((line = error.readLine()) != null) {
            errStr.append(line);
            outStr.append(System.lineSeparator());
        }
        int exitCode = process.waitFor();
        log.trace("Execute: `{}`, code: {}, time: {} workdir: {}", cmd, exitCode, timer.stop(), workdir.getPath());

        var result = new HashMap<String, String>();
        result.put("stdout", outStr.toString().trim());
        result.put("stderr", errStr.toString().trim());
        result.put("code", String.valueOf(exitCode));
        return result;
    }

    private static BufferedReader getOutput(Process p) {
        return new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
    }

    private static BufferedReader getError(Process p) {
        return new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));
    }

    private String getInitDirPath(String projectName) {
        var dirname = projectName.replaceAll("[^A-Za-z0-9\\-_.]", ".");
        return System.getProperty("user.dir") + File.separator +
                configuration.getStage().getTempDir() + File.separator + dirname + "/" +
                "init";
    }

    private String getMergeDirPath(Merge merge) {
        var projectName = merge.getProjectName();
        var dirname = projectName.replaceAll("[^A-Za-z0-9\\-_.]", ".");
        return System.getProperty("user.dir") + File.separator +
                configuration.getStage().getTempDir() + File.separator + dirname + "/" +
                DateTimeUtil.getDate("yyyy.MM.dd_HH:mm.ss") + "R" + Integer.toHexString(Integer.parseInt(StringUtil.random(1000, 9999)));
    }
}
