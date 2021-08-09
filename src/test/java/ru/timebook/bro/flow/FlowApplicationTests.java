package ru.timebook.bro.flow;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Assert;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.utils.DateTimeUtil;
import ru.timebook.bro.flow.utils.GravatarUtil;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


@ExtendWith(SpringExtension.class)
@Import({Config.class})
@SpringBootTest
class FlowApplicationTests {
    private final static Logger logger = LoggerFactory.getLogger(FlowApplicationTests.class);
    @Autowired
    private Config config;

    @Test
    @Disabled
    void httpsTest() {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target("https://ya.ru");
        Invocation.Builder invocationBuilder = webTarget.request();
        invocationBuilder.accept("application/json");
        Response response = invocationBuilder.get();
        Assert.isTrue(response.getStatus() == 200, "HTTPS uri successful loaded");
    }

    @Test
    void durationDayTest() {
        var duration = Duration.parse(config.getTaskTrackers().getRedmine().getAfterUpdateTime());
        var localDate = ZonedDateTime.now().plusSeconds(duration.getSeconds());
        var date = localDate.format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm Z"));
        logger.info("result: {}", date);
        Assert.isTrue(date.length() == 20, "Length equal format");
    }

    @Test
    void durationDayTimeTest() {
        var localDate = DateTimeUtil.duration("PT-1H");
        var date = localDate.format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm"));
        logger.info("result PT-1H: {}", date);
        Assert.isTrue(date.length() == 14, "Length equal format");
    }

    @Test
    void gravatarHashTest() {
        var email = "bender.rodriguesz@mail.zone";
        var uri = GravatarUtil.getUri(email, 50);
        Assert.isTrue(uri.contains("ed1c4c31a6bd71ea498394438d37f444"), "Hash part success calculates");
        Assert.isTrue(uri.contains("size=50"), "Size exists");
    }
}
