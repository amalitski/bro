package ru.timebook.bro.flow;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;
import ru.timebook.bro.flow.configurations.Configuration;
import ru.timebook.bro.flow.modules.build.BuildRepository;
import ru.timebook.bro.flow.modules.build.ExecutionService;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

@SpringBootTest
public class ExecutionServiceIntegrationTest {
    private final static Logger logger = LoggerFactory.getLogger(ExecutionServiceIntegrationTest.class);
    @Autowired
    private ExecutionService executionService;
    @Autowired
    private Configuration configuration;
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
        WebTarget webTarget = client.target(configuration.getRepositories().getGitlab().getHost());
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

}
