package ru.timebook.bro.flow;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.modules.build.BuildHasProjectRepository;
import ru.timebook.bro.flow.modules.build.BuildRepository;
import ru.timebook.bro.flow.modules.build.ExecutionService;
import ru.timebook.bro.flow.modules.build.MergeService;
import ru.timebook.bro.flow.modules.git.GitlabGitRepository;
import ru.timebook.bro.flow.modules.taskTracker.TaskTracker;
import ru.timebook.bro.flow.utils.DateTimeUtil;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

@Slf4j
@SpringBootTest
@Disabled
public class ExecutionServiceIntegrationTest {
    @Autowired
    private ExecutionService executionService;
    @Autowired
    private GitlabGitRepository gitlabGitRepository;
    @Autowired
    private Config config;
    @Autowired
    private MergeService mergeService;
    @Autowired
    private BuildRepository buildRepository;
    @Autowired
    private BuildHasProjectRepository buildHasProjectRepository;
    @Autowired
    private List<TaskTracker> taskTrackers;

    @Test
    void httpsTest() {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(config.getRepositories().getGitlab().getHost());
        Invocation.Builder invocationBuilder = webTarget.request();
        invocationBuilder.accept("application/json");
        Response response = invocationBuilder.get();
        log.info("Response status: {}", response.getStatus());
        Assert.isTrue(response.getStatus() >= 200, "Load HTTPS uri");
    }

    @Test
    void buildRelationTest() {
        var b = buildRepository.findFirstByOrderByStartAtDesc();
        b.get().getBuildHasProjects().forEach(buildHasProject -> log.info("{}", buildHasProject.getId()));
    }

    @Test
    void removeOldTest() {
        var dir  = System.getProperty("user.dir") + File.separator + config.getStage().getTemp().getTempDir();
        getDirectories(dir).forEach(f -> {
            var duration = Duration.parse(config.getStage().getTemp().getCleanAfter());
            var olden = Instant.now().plusSeconds(duration.getSeconds());
            if (FileUtils.isFileOlder(f, olden)) {
                log.info("Dir old: {} / {}", f.getName(), olden);

            } else {
                getDirectories(f.getAbsolutePath()).stream()
                        .filter(pProject -> !pProject.getName().equals(config.getStage().getTemp().getInitDir()))
                        .forEach(fProject -> {
                        log.info("Dir projects: {}/{}", f.getName(), fProject.getName());
                });
            }
        });
    }

    private List<File> getDirectories(String path){
        File file = new File(path);
        File[] directories = file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        });
        if (directories == null){
            return List.of();
        }
        return List.of(directories);
    }

    @Test
    void gitlabAvatarUriRepoTest() {
        gitlabGitRepository.getCommitterAvatarUri("linus.torvalds@gmail.com");
    }

    @Autowired
    private DateTimeUtil dateTimeUtil;
    @Test
    void gitqw() throws GitLabApiException {
        var hash = getBranchCheckSum("timebook/timebook", "27449-outsource-market");
        log.trace("{}", hash);

    }

    private String getBranchCheckSum(String projectName, String branchName){
        ZoneId systemTimeZone = ZoneId.systemDefault();
        var since = Date.from(dateTimeUtil.duration("P-30D").toLocalDate().atStartOfDay(systemTimeZone).toInstant());
        var until = Date.from(dateTimeUtil.duration("P+1D").toLocalDate().atStartOfDay(systemTimeZone).toInstant());
        var commitBuffer = new StringBuffer();
        try {
            gitlabGitRepository.getApi().getCommitsApi()
                    .getCommits(projectName, branchName, since, until, 100).stream().forEach(c -> {
                commitBuffer.append(c.getMessage());
            });
        } catch (GitLabApiException e){
            log.error("Exception", e);
        }
        return DigestUtils.md5DigestAsHex(commitBuffer.toString().getBytes(StandardCharsets.UTF_8));
    }
}
