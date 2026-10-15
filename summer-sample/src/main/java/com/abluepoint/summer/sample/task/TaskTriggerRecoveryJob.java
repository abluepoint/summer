package com.abluepoint.summer.sample.task;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Retries committed trigger reservations that have not started an execution. */
@DisallowConcurrentExecution
public class TaskTriggerRecoveryJob extends QuartzJobBean {
    @Autowired private TaskSchedulerService tasks;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        tasks.dispatchPendingTriggers();
    }
}
