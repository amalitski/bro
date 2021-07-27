package ru.timebook.bro.flow;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.utils.GravatarUtil;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@SpringBootTest
@Disabled
class FlowApplicationTests {
    private final static Logger logger = LoggerFactory.getLogger(FlowApplicationTests.class);
    @Autowired
    private Config config;

/*     @Test
    void httpsTest() {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target("https://ya.ru");
        Invocation.Builder invocationBuilder = webTarget.request();
        invocationBuilder.accept("application/json");
        Response response = invocationBuilder.get();
        Assert.isTrue(response.getStatus() == 200, "HTTPS uri successful loaded");
    } */

    @Test
    void configTest() {
        Assert.hasText(config.getStage().getBranchName(), config.getStage().getPushCmd());
    }

    @Test
    void durationDayTest() {
        var duration = Duration.parse(config.getTaskTrackers().getRedmine().getAfterUpdateTime());
        var localDate = ZonedDateTime.now().plusSeconds(duration.getSeconds());
        logger.info("result: {}", localDate.format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm Z")));
    }

    @Test
    void gravatarHashTest() {
        var email = "bender.rodriguesz@mail.zone";
        var uri = GravatarUtil.getUri(email, 50);
        Assert.isTrue(uri.contains("ed1c4c31a6bd71ea498394438d37f444"), "Hash part success calculates");
        Assert.isTrue(uri.contains("size=50"), "Size exists");
    }
}
