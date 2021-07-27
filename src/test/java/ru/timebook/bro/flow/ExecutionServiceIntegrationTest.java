package ru.timebook.bro.flow;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.util.Assert;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.modules.build.BuildRepository;
import ru.timebook.bro.flow.modules.build.ExecutionService;
import ru.timebook.bro.flow.modules.git.GitlabGitRepository;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@SpringBootTest
@Disabled
public class ExecutionServiceIntegrationTest {
    private final static Logger logger = LoggerFactory.getLogger(ExecutionServiceIntegrationTest.class);
    @Autowired
    private ExecutionService executionService;
    @Autowired
    private GitlabGitRepository gitlabGitRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private Config config;
    @Autowired
    private BuildRepository buildRepository;
    @Test
    @Disabled
    void middlewareTest() throws Exception {
        var response = executionService.mergeAndPush();
//        executionService.setDeployed(response.getIssues());

        var out = executionService.getOut(response.getIssues(), response.getMerges());
        logger.info(out);
    }

    @Test
    void httpsTest() {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(config.getRepositories().getGitlab().getHost());
        Invocation.Builder invocationBuilder = webTarget.request();
        invocationBuilder.accept("application/json");
        Response response = invocationBuilder.get();
        logger.info("Response status: {}", response.getStatus());
        Assert.isTrue(response.getStatus() >= 200, "Load HTTPS uri");
    }

    @Test
    void buildRelationTest() {
        var b = buildRepository.findFirstByOrderByStartAtDesc();
        b.get().getBuildHasProjects().forEach(buildHasProject -> logger.info("{}", buildHasProject.getId()));
    }

    @Test
    void removeOldTest() {
        var dir  = System.getProperty("user.dir") + File.separator + config.getStage().getTemp().getTempDir();
        getDirectories(dir).forEach(f -> {
            var duration = Duration.parse(config.getStage().getTemp().getCleanAfter());
            var olden = Instant.now().plusSeconds(duration.getSeconds());
            if (FileUtils.isFileOlder(f, olden)) {
                logger.info("Dir old: {} / {}", f.getName(), olden);

            } else {
                getDirectories(f.getAbsolutePath()).stream()
                        .filter(pProject -> !pProject.getName().equals(config.getStage().getTemp().getInitDir()))
                        .forEach(fProject -> {
                        logger.info("Dir projects: {}/{}", f.getName(), fProject.getName());
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
        gitlabGitRepository.getCommitterAvatarUri("linus.torvalds@gmail.com");
        gitlabGitRepository.getCommitterAvatarUri("linus.torvalds@gmail.com");
        gitlabGitRepository.getCommitterAvatarUri("linus.torvalds@gmail.com");
    }
}
