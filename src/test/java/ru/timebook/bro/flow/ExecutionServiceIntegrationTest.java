package ru.timebook.bro.flow;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.timebook.bro.flow.modules.build.ExecutionService;

@SpringBootTest
public class ExecutionServiceIntegrationTest {
    private final static Logger logger = LoggerFactory.getLogger(ExecutionServiceIntegrationTest.class);
    @Autowired
    private ExecutionService executionService;

    @Test
    @Disabled
    void middlewareTest() throws Exception {
        var response = executionService.mergeAndPush();
//        executionService.setDeployed(response.getIssues());

        var out = executionService.getOut(response.getIssues(), response.getMerges());
        logger.info(out);
    }
}
