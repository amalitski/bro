package ru.timebook.bro.flow;

import com.google.common.collect.Lists;
import org.codehaus.groovy.tools.StringHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;
import org.springframework.util.DigestUtils;
import ru.timebook.bro.flow.configurations.Configuration;
import ru.timebook.bro.flow.modules.build.*;
import ru.timebook.bro.flow.utils.GravatarUtil;
import ru.timebook.bro.flow.utils.StringUtil;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class FlowApplicationTests {
    private final static Logger logger = LoggerFactory.getLogger(FlowApplicationTests.class);
    @Autowired
    private Configuration configuration;
    @Autowired
    private BuildRepository buildRepository;
    @Autowired
    private BuildHasProjectRepository buildHasProjectRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void httpsTest() {
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target("https://ya.ru");
        Invocation.Builder invocationBuilder = webTarget.request();
        invocationBuilder.accept("application/json");
        Response response = invocationBuilder.get();
        Assert.isTrue(response.getStatus() == 200, "HTTPS uri successful loaded");
    }

    @Test
    void configTest() {
        Assert.hasText(configuration.getStage().getBranchName(), configuration.getStage().getPushCmd());
    }

    @Test
    void durationDayTest() {
        var duration = Duration.parse(configuration.getTaskTrackers().getRedmine().getAfterUpdateTime());
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

    @Test
    void databaseTest() {
//        var pList = new ArrayList<Project>();
//        for (String pName: List.of("timebook/timebook", "timebook/timebook.web-client")) {
//            var p = projectRepository.findByName(pName).orElse(new Project());
//            p.setName(pName);
//            p.setPushedAt(LocalDateTime.now());
//            p.setBuildCheckSum(StringUtil.random(1000, 9999));
//            projectRepository.save(p);
//            pList.add(projectRepository.save(p));
//        }
//        var b = buildRepository.save(Build.builder().startAt(LocalDateTime.now()).build());
//        var bhp = pList.stream().map(p -> BuildHasProject.builder().project(p).build(b).mergesJson(("ML").mergeCheckSum("CS").build()).collect(Collectors.toList());
//        buildHasProjectRepository.saveAll(bhp);

//        var bhp = pList.stream().map(p -> BuildHasProject.builder().buildOutput("Out").buildCheckSum("CS").build()).collect(Collectors.toList());

//        Build.builder().logs().





//        var p = BuildHasProject.builder().build();
//        var b = Build.builder().logs("123").buildHasProject(p).build();
//        buildRepository.save(b);


//        buildRepository.save()

    }


}
