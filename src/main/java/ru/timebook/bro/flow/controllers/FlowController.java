package ru.timebook.bro.flow.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import ru.timebook.bro.flow.modules.build.FlowService;

import java.util.Map;

@Slf4j
@RestController
public class FlowController {
    private final FlowService flowService;

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    @GetMapping(path = "/flow")
    public ModelAndView index(Map<String, Object> model) throws Exception {
        var b = flowService.getLastBuild();
        model.put("data", b);
        return new ModelAndView("index", model);
    }
}
