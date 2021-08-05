package ru.timebook.bro.flow.schedulers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.timebook.bro.flow.modules.build.ExecutionService;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@EnableAsync
public class FlowSchedule {
    private final Semaphore lockJob = new Semaphore(1);
    private final Semaphore lockMerge = new Semaphore(1);
    private final ExecutionService executionService;

    public FlowSchedule(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @Async
    @Scheduled(cron = "${bro.flow.stage.cronReceive}")
    public void refreshIssues() {
        try {
            if (lockMerge.tryAcquire(15, TimeUnit.MINUTES)) {
                executionService.mergeAndPush();
            }
        } catch (Exception e) {
            log.error("Exception: ", e);
        } finally {
            lockMerge.release();
        }
    }

    @Async
    @Scheduled(cron = "${bro.flow.stage.git.cronReceive}")
    public void refreshGit() {
        try {
            if (lockJob.tryAcquire(15, TimeUnit.MINUTES)) {
                executionService.checkJobAndUpdateIssue();
            }
        } catch (Exception e) {
            log.error("Exception: ", e);
        } finally {
            lockJob.release();
        }
    }
}
