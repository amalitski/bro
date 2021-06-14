package ru.timebook.bro.flow.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import ru.timebook.bro.flow.modules.build.ExecutionService;
import ru.timebook.bro.flow.modules.build.FlowService;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
public class FlowController {
    private final FlowService flowService;
    private final Semaphore lock = new Semaphore(1);
    private final ExecutionService executionService;

    public FlowController(FlowService flowService, ExecutionService executionService) {
        this.flowService = flowService;
        this.executionService = executionService;
    }

    @GetMapping(path = "/flow")
    public ModelAndView index(Map<String, Object> model) throws Exception {
        var b = flowService.getLastBuild();
        model.put("data", b);
        return new ModelAndView("index", model);
    }

    @Scheduled(cron = "${bro.flow.stage.cronReceive}")
    public void refresh(){
        try {
            if (lock.tryAcquire( 60, TimeUnit.SECONDS)) {
                executionService.mergeAndPush();
            }
        } catch (Exception e) {
            log.error("Exception: ", e);
        } finally {
            lock.release();
        }
    }
}
