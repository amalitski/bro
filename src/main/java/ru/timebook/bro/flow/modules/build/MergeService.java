package ru.timebook.bro.flow.modules.build;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.modules.taskTracker.Issue;
import ru.timebook.bro.flow.modules.git.Merge;
import ru.timebook.bro.flow.utils.DateTimeUtil;
import ru.timebook.bro.flow.utils.StringUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MergeService {
    private final Config config;
    private final ProjectRepository projectRepository;

    public MergeService(Config config, ProjectRepository projectRepository) {
        this.config = config;
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

    public void clean() {
        var dir = System.getProperty("user.dir") + File.separator + config.getStage().getTemp().getTempDir();
        var dirsRemove = new ArrayList<File>();
        getDirectories(dir).parallelStream().forEach(f -> {
            var duration = Duration.parse(config.getStage().getTemp().getCleanAfter());
            var olden = Instant.now().plusSeconds(duration.getSeconds());
            if (FileUtils.isFileOlder(f, olden)) {
                dirsRemove.add(f);
            } else {
                dirsRemove.addAll(getDirectories(f.getAbsolutePath()).stream()
                        .filter(pProject -> !pProject.getName().equals(config.getStage().getTemp().getInitDir()))
                        .collect(Collectors.toList()));
            }
        });
        dirsRemove.parallelStream().forEach(f -> {
            try {
                FileUtils.deleteDirectory(f);
                log.trace("Remove directory: {}", f.getAbsolutePath());
            } catch (IOException e) {
                log.error("Exception", e);
            }
        });
    }

    public void push(List<Merge> merges) throws Exception {
        merges.forEach(m -> {
            if (!m.getInitSuccess()) {
                log.error("Skip push command, because init repo with error. See init logs: {}", m.getInitStdout());
                return;
            }
            try {
                pushExec(m);
            } catch (Exception e) {
                log.error("Push command return exception", e);
            }
        });
    }

    private void pushExec(Merge merge) throws Exception {
        if (config.getStage().getBranchName().isEmpty() || config.getStage().getPushCmd().isEmpty()) {
            throw new Exception("Invalid configuration: bro.flow.stage.branchName or bro.flow.stage.pushCmd empty!");
        }
        var project = projectRepository.findByName(merge.getProjectName());
        if (project.isPresent() && project.get().getBuildCheckSum() != null && project.get().getBuildCheckSum().equals(merge.getCheckSum())) {
            log.trace("Push skipped. Project '{}', checksum equal {}", merge.getProjectName(), project.get().getBuildCheckSum());
            return;
        }
        var cmd = config.getStage().getPushCmd();
        var resp = exec(cmd, merge.getDirRepo());
        merge.getPush().setLog(resp.get("stdout"));
        merge.getPush().setPushed(resp.get("code").equals("0"));
        if (merge.getPush().isPushed()) {
            log.debug("Pushed project {}:{}", merge.getProjectName(), config.getStage().getBranchName());
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
                    var committer = Issue.Committer.builder()
                            .avatarUri(c.getCommitterAvatarUri())
                            .name(c.getCommitterName())
                            .build();
                    committers.add(committer);
                });
            }
        });
        issue.setCommitters(committers.stream().toList());

    }

    private void initRepo(Merge merge) throws Exception {
        var dirname = getInitDirPath(merge);
        var dir = new File(dirname);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("Failed to create init directory: " + dirname);
        }
        var f = dir.listFiles();
        if (f != null && f.length == 0) {
            var resp = exec("git clone " + merge.getSshUrlRepo() + " ./", dir);
            merge.setInitStdout(getOutPretty(resp));
            merge.setInitCode(resp.get("code"));
            if (!resp.get("code").equals("0")) {
                throw new Exception(String.format("Clone %s repository with error. See logs for detail information. ", merge.getSshUrlRepo()));
            }
            var cmd = String.format(
                    "git config user.name %s && git config user.email %s",
                    config.getStage().getGit().getUserName(),
                    config.getStage().getGit().getUserEmail()
            );
            exec(cmd, dir);
        }
    }

    private String getOutPretty(HashMap<String, String> resp) {
        return String.format("%s (code %s)%n%s", resp.get("cmd"), resp.get("code"), resp.get("stdout"));
    }

    private void mergeRepo(Merge merge) throws Exception {
        var dirname = getMergeDirPath(merge);
        var dirMerge = new File(dirname);
        var dirRepo = new File(dirname + File.separator + "repo");
        var dirInit = new File(getInitDirPath(merge));
        if (!dirMerge.exists() && !dirMerge.mkdirs()) {
            throw new Exception("Failed to create merge directory: " + dirname);
        } else if (!dirRepo.mkdirs()) {
            throw new Exception("Failed to create repo directory: " + dirRepo.getPath());
        }
        merge.setDirRepo(dirRepo);
        merge.setDirMerge(dirMerge);
        var respFetch = exec("git fetch origin", dirInit);
        var out = merge.getInitStdout() == null ?
                getOutPretty(respFetch) : String.format("%s%n%n%s", merge.getInitStdout(), getOutPretty(respFetch));
        merge.setInitStdout(out);
        merge.setInitCode(respFetch.get("code"));
        if (!respFetch.get("code").equals("0")) {
            throw new Exception("Failed to fetch origin: " + dirInit.getPath());
        }

        FileUtils.copyDirectory(dirInit, dirRepo);
        mergeRecursive(merge, dirRepo);

        var log = exec("git log -25 --pretty=format:'%cd \\t %an : %s'", dirRepo);
        merge.setLog(log.get("stdout"));
        var restCheckSum = exec("git log -1000 --pretty=format:\"%s\"", dirRepo);
        if (!restCheckSum.get("code").equals("0")) {
            var msg = String.format("Calculate checksum with error. Cmd '%s' dir %s", restCheckSum.get("cmd"), dirRepo);
            throw new Exception(msg);
        }
        var checkSum = DigestUtils.md5DigestAsHex(restCheckSum.get("stdout").getBytes(StandardCharsets.UTF_8));
        merge.setCheckSum(checkSum);

        var lastCommit = exec("git log -1 --pretty=%H", dirRepo);
        merge.setLastCommitSha(lastCommit.get("stdout").trim());
    }

    private boolean mergeRecursive(Merge merge, File dirRepo) throws IOException {
        var branchFirstOpt = merge.getBranches().stream().findFirst();
        if (branchFirstOpt.isEmpty()){
            return true;
        }
        var branchFirst = branchFirstOpt.get();
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
            if (branch.isMerged()) {
                log.trace("Skip branch `{}`. The branch was merged.", branch.getBranchName());
                continue;
            }
            branch.setCommits(getCommits(branch, dirRepo));
            var resp = exec(getMergeCmd(branch), dirRepo);
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

    private String getMergeCmd(Merge.Branch branch){
        return config.getStage().getMergeCmd()
                .replace("{branchName}", branch.getBranchName());
    }

    private List<Merge.Branch.Commit> getCommits(Merge.Branch branch, File dirRepo) throws IOException {
        var commits = new ArrayList<Merge.Branch.Commit>();
        var cmdLog = MessageFormat.format("git log --pretty=format:\"%H|||%ce|||%cn|||%ci|||%s\" origin/{1}..origin/{0}", branch.getBranchName(), branch.getTargetBranchName());
        var respLog = exec(cmdLog, dirRepo);
        var data = respLog.get("stdout").trim();
        if (data.isEmpty()) {
            return commits;
        }
        Arrays.stream(data.split("\n")).forEach(row -> {
            var parts = row.trim().split(Pattern.quote("|||"));
            if (parts.length >= 5 && !parts[0].isEmpty() && !parts[1].isEmpty() && !parts[2].isEmpty()) {
                commits.add(Merge.Branch.Commit.builder()
                        .hash(parts[0].trim())
                        .committerEmail(parts[1].trim())
                        .committerName(parts[2].trim())
                        .committerDate(parts[3].trim())
                        .subject(parts[4].trim())
                        .build());
            }
        });
        return commits;
    }

    private HashMap<String, String> exec(String cmd, File workdir) throws IOException {
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

        var result = new HashMap<String, String>();
        result.put("cmd", cmd);
        try {
            int exitCode = process.waitFor();
            result.put("stdout", outStr.toString().trim());
            result.put("stderr", errStr.toString().trim());
            result.put("code", String.valueOf(exitCode));

        } catch (InterruptedException e) {
            log.error("Exception", e);
            result.put("stdout", e.getMessage());
            result.put("stderr", e.getMessage());
            result.put("code", String.valueOf(1));
        } finally {
            process.destroy();
        }
        log.trace("Execute: `{}`, code: {}, time: {}, workdir: {}", cmd, result.get("code"), timer.stop(), workdir.getPath());
        return result;
    }

    private BufferedReader getOutput(Process p) {
        return new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
    }

    private BufferedReader getError(Process p) {
        return new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8));
    }

    private String getProjectDirPath(Merge merge) {
        var dirname = merge.getProjectSafeName();
        return System.getProperty("user.dir") + File.separator +
                config.getStage().getTemp().getTempDir() + File.separator + dirname;
    }

    private String getInitDirPath(Merge merge) {
        return getProjectDirPath(merge) + File.separator + config.getStage().getTemp().getInitDir();
    }

    private String getMergeDirPath(Merge merge) {
        return getProjectDirPath(merge) + File.separator +
                DateTimeUtil.getDate("yyyy.MM.dd_HH:mm.ss") + "R" + Integer.toHexString(Integer.parseInt(StringUtil.random(1000, 9999)));
    }

    private List<File> getDirectories(String path) {
        File file = new File(path);
        File[] directories = file.listFiles(File::isDirectory);
        if (directories == null) {
            return List.of();
        }
        return List.of(directories);
    }
}
