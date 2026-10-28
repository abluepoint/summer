package com.abluepoint.summer.sample.task.infrastructure;
import com.abluepoint.summer.sample.task.application.TaskExecutionService;

import org.quartz.JobExecutionContext;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class QuartzTaskJob extends QuartzJobBean {
    @Autowired
    private TaskExecutionService executionService;
    public void setExecutionService(TaskExecutionService executionService) { this.executionService = executionService; }
    @Override protected void executeInternal(JobExecutionContext context) {
        String taskId = context.getMergedJobDataMap().getString("taskId");
        String suppliedKey = context.getMergedJobDataMap().getString("idempotencyKey");
        String scheduleKey = suppliedKey == null || suppliedKey.isBlank()
                ? taskId + ":" + context.getScheduledFireTime().toInstant().toEpochMilli() : suppliedKey;
        executionService.execute(taskId, context.getFireInstanceId(), scheduleKey,
                context.getScheduledFireTime().toInstant(), context.isRecovering());
    }
}
