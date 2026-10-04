package com.abluepoint.summer.sample.key;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class AutomaticKeyRotationJob extends QuartzJobBean {
    @Autowired
    private AutomaticKeyRotationService rotationService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        rotationService.rotateIfDue();
    }
}
