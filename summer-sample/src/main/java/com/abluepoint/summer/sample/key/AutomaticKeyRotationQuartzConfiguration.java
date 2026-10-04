package com.abluepoint.summer.sample.key;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "mysql")
class AutomaticKeyRotationQuartzConfiguration {
    private static final JobKey JOB = new JobKey("automatic-key-rotation", "summer-security");
    private static final TriggerKey TRIGGER = new TriggerKey("automatic-key-rotation", "summer-security");
    private final Scheduler scheduler;

    AutomaticKeyRotationQuartzConfiguration(Scheduler scheduler) { this.scheduler = scheduler; }

    @EventListener(ContextRefreshedEvent.class)
    void registerRotationJob() {
        try {
            if (scheduler.checkExists(JOB)) return;
            JobDetail detail = JobBuilder.newJob(AutomaticKeyRotationJob.class).withIdentity(JOB).storeDurably().build();
            CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(TRIGGER).forJob(detail)
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?")
                            .withMisfireHandlingInstructionFireAndProceed()).build();
            scheduler.scheduleJob(detail, trigger);
        } catch (ObjectAlreadyExistsException ignored) {
            // Another Quartz cluster node registered the same durable job.
        } catch (SchedulerException failure) {
            throw new IllegalStateException("SUMMER_KEY_ROTATION_SCHEDULER_UNAVAILABLE", failure);
        }
    }
}
