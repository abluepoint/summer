package com.abluepoint.summer.sample.task;

import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.beans.factory.annotation.Autowired;

public class QuartzTaskJob extends QuartzJobBean {
    @Autowired
    private TaskExecutionService executionService;
    public void setExecutionService(TaskExecutionService executionService) { this.executionService = executionService; }
    @Override protected void executeInternal(JobExecutionContext context) {
        String taskId = context.getMergedJobDataMap().getString("taskId");
        executionService.execute(taskId, context.getFireInstanceId());
    }
}
