package ru.timebook.bro.flow.controllers;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import ru.timebook.bro.flow.configs.Config;
import ru.timebook.bro.flow.modules.build.FlowService;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

@Slf4j
@RestController
public class FlowController {
    private final FlowService flowService;
    private final Config config;

    public FlowController(FlowService flowService, Config config) {
        this.flowService = flowService;
        this.config = config;
    }

    @Data
    public static class Helper {
        private Mustache.Lambda hasDeployed = new Mustache.Lambda() {
            @Override
            public void execute(Template.Fragment frag, Writer out) throws IOException {
                var c = switch (frag.execute()) {
                    case "success" -> "badge-outline-success";
                    case "failed", "canceled", "skipped" -> "badge-outline-danger";
                    default -> "badge-outline-warning";
                };
                out.write(c);
            }
        };
    }

    @GetMapping(path = "/flow")
    public ModelAndView index(Map<String, Object> model) throws Exception {
        var b = flowService.getLastBuild();
        model.put("data", b);
        model.put("helper", new Helper());
        model.put("path", config.getStage().getBasePath());
        return new ModelAndView("flow", model);
    }
}
