package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.sample.task.application.TaskCreateCommand;
import com.abluepoint.summer.sample.task.application.TaskSchedulerService;
import com.abluepoint.summer.sample.task.domain.TaskSchedule;
import com.abluepoint.summer.sample.task.domain.TaskConflictException;
import com.abluepoint.summer.sample.task.domain.TaskStatus;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.dao.DataAccessException;
import com.abluepoint.summer.sample.task.infrastructure.QuartzTaskJob;
import com.abluepoint.summer.sample.task.infrastructure.TaskTriggerRecoveryJob;
import com.abluepoint.summer.sample.task.infrastructure.QuartzTaskSchedulingAdapter;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.impl.matchers.KeyMatcher;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Upgrade the persisted Quartz representation, then execute the real application job. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:quartz-upgrade-${random.uuid};DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.quartz.job-store-type=jdbc",
        "spring.quartz.jdbc.initialize-schema=always",
        "spring.quartz.auto-startup=false",
        "summer.security.storage.keys=memory",
        "summer.sample.cache.enabled=false",
        "summer.sample.resilience.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaskQuartzJobUpgradeTest {
    @Autowired Scheduler scheduler;
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskSchedulerService tasks;
    @Autowired QuartzTaskSchedulingAdapter scheduling;

    @Test
    void upgradesAnUnreadableTaskJobWithoutLosingItsDataOrPausedTrigger() throws Exception {
        assertThat(scheduler.getMetaData().isJobStoreSupportsPersistence()).isTrue();
        var task = tasks.create("ent-acme", new TaskCreateCommand("Upgrade", "system.noop",
                "0 0 0 1 1 ? 2099", Map.of(), 0), "quartz-upgrade", null);
        JobKey key = new JobKey("task-" + task.taskId(), "summer-task-ent-acme");
        TriggerKey triggerKey = new TriggerKey("trigger-" + task.taskId(), key.getGroup());
        JobDataMap data = scheduler.getJobDetail(key).getJobDataMap();
        data.put("upgradeMarker", List.of("retain", 42));
        scheduler.addJob(scheduler.getJobDetail(key).getJobBuilder().usingJobData(data)
                .requestRecovery(false).build(), true);
        scheduler.pauseTrigger(triggerKey);
        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
        makeLegacyClassUnreadable(key, "com.abluepoint.summer.sample.task.QuartzTaskJob");

        scheduling.scheduleExisting(new TaskSchedule(task.taskId(), "ent-acme", "0 0 0 1 1 ? 2099"));

        JobDetail upgraded = scheduler.getJobDetail(key);
        assertThat(upgraded.getJobClass()).isEqualTo(QuartzTaskJob.class);
        assertThat(upgraded.getJobDataMap()).containsExactlyInAnyOrderEntriesOf(data);
        assertThat(upgraded.requestsRecovery()).isTrue();
        assertThat(upgraded.isDurable()).isTrue();
        assertPausedTriggerUnchanged(trigger);
        executeAndAssertData(key, data);
        assertPausedTriggerUnchanged(trigger);
        assertThat(tasks.executions("ent-acme", task.taskId()).items())
                .extracting(execution -> execution.status()).contains("SUCCESS");
    }

    @Test
    void upgradesTheUnreadableMaintenanceJobAndRetainsItsExistingPausedTrigger() throws Exception {
        JobKey key = new JobKey("trigger-recovery", "summer-internal");
        JobDetail stored = scheduler.getJobDetail(key);
        JobDataMap data = stored.getJobDataMap();
        data.put("maintenanceMarker", List.of("keep", 7));
        scheduler.addJob(stored.getJobBuilder().usingJobData(data).requestRecovery(false).build(), true, true);
        TriggerKey triggerKey = new TriggerKey("trigger-recovery", "summer-internal");
        scheduler.pauseTrigger(triggerKey);
        Trigger trigger = scheduler.getTrigger(triggerKey);
        makeLegacyClassUnreadable(key, "com.abluepoint.summer.sample.task.TaskTriggerRecoveryJob");

        scheduling.initializeRecovery();

        JobDetail upgraded = scheduler.getJobDetail(key);
        assertThat(upgraded.getJobClass()).isEqualTo(TaskTriggerRecoveryJob.class);
        assertThat(upgraded.getJobDataMap()).containsExactlyInAnyOrderEntriesOf(data);
        assertThat(upgraded.isDurable()).isEqualTo(stored.isDurable());
        assertThat(upgraded.requestsRecovery()).isTrue();
        assertPausedTriggerUnchanged(trigger);
        executeAndAssertData(key, data);
        assertPausedTriggerUnchanged(trigger);
    }

    @Test
    void repairsPausedJobsDuringBeanInitializationBeforeTheSchedulerCanLoadThem() throws Exception {
        var task = tasks.create("ent-acme", new TaskCreateCommand("Paused upgrade", "system.noop",
                "0 0 0 1 1 ? 2099", Map.of(), 0), "quartz-paused-upgrade", null);
        tasks.setStatus("ent-acme", task.taskId(), TaskStatus.PAUSED, "\"" + task.version() + "\"", "pause-upgrade", null);
        JobKey taskKey = new JobKey("task-" + task.taskId(), "summer-task-ent-acme");
        JobKey recoveryKey = new JobKey("trigger-recovery", "summer-internal");
        Trigger paused = scheduler.getTrigger(new TriggerKey("trigger-" + task.taskId(), taskKey.getGroup()));
        JobDetail taskBefore = scheduler.getJobDetail(taskKey);
        JobDetail recoveryBefore = scheduler.getJobDetail(recoveryKey);
        makeLegacyClassUnreadable(taskKey, "com.abluepoint.summer.sample.task.QuartzTaskJob");
        makeLegacyClassUnreadable(recoveryKey, "com.abluepoint.summer.sample.task.TaskTriggerRecoveryJob");
        assertThat(scheduler.isStarted()).isFalse();

        try (var context = newUpgradeContext(new QuartzProperties())) {
            context.refresh();
            assertThat(scheduler.isStarted()).isFalse();
            assertThat(scheduler.getJobDetail(taskKey).getJobClass()).isEqualTo(QuartzTaskJob.class);
            assertThat(scheduler.getJobDetail(recoveryKey).getJobClass()).isEqualTo(TaskTriggerRecoveryJob.class);
            assertThat(scheduler.getJobDetail(taskKey).getJobDataMap()).isEqualTo(taskBefore.getJobDataMap());
            assertThat(scheduler.getJobDetail(taskKey).requestsRecovery()).isEqualTo(taskBefore.requestsRecovery());
            assertThat(scheduler.getJobDetail(recoveryKey).requestsRecovery()).isEqualTo(recoveryBefore.requestsRecovery());
            assertPausedTriggerUnchanged(paused);
        }
    }

    @Test
    void failsInitializationWhenTheConfiguredQuartzStoreCannotBeMigrated() {
        QuartzProperties properties = new QuartzProperties();
        properties.getProperties().put("org.quartz.jobStore.tablePrefix", "MISSING_");
        try (var context = newUpgradeContext(properties)) {
            assertThatThrownBy(context::refresh).isInstanceOf(TaskConflictException.class)
                    .hasCauseInstanceOf(DataAccessException.class);
        }
    }

    private GenericApplicationContext newUpgradeContext(QuartzProperties properties) {
        var context = new GenericApplicationContext();
        // This context observes startup ordering but does not own the test application's scheduler.
        context.registerBean(Scheduler.class, () -> scheduler, definition -> definition.setDestroyMethodName(""));
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.registerBean(QuartzProperties.class, () -> properties);
        context.registerBean(QuartzTaskSchedulingAdapter.class);
        return context;
    }

    private void makeLegacyClassUnreadable(JobKey key, String legacyClass) throws Exception {
        assertThat(jdbc.update("UPDATE QRTZ_JOB_DETAILS SET JOB_CLASS_NAME=? WHERE SCHED_NAME=? AND JOB_NAME=? AND JOB_GROUP=?",
                legacyClass, scheduler.getSchedulerName(), key.getName(), key.getGroup())).isEqualTo(1);
        assertThatThrownBy(() -> scheduler.getJobDetail(key))
                .isInstanceOf(SchedulerException.class).hasRootCauseInstanceOf(ClassNotFoundException.class);
    }

    private void assertPausedTriggerUnchanged(Trigger before) throws Exception {
        Trigger after = scheduler.getTrigger(before.getKey());
        assertThat(after.getJobKey()).isEqualTo(before.getJobKey());
        if (before instanceof CronTrigger cron) {
            assertThat(((CronTrigger) after).getCronExpression()).isEqualTo(cron.getCronExpression());
            assertThat(((CronTrigger) after).getTimeZone()).isEqualTo(cron.getTimeZone());
        } else if (before instanceof SimpleTrigger simple) {
            assertThat(((SimpleTrigger) after).getRepeatInterval()).isEqualTo(simple.getRepeatInterval());
            assertThat(((SimpleTrigger) after).getRepeatCount()).isEqualTo(simple.getRepeatCount());
        }
        assertThat(after.getEndTime()).isEqualTo(before.getEndTime());
        assertThat(after.getCalendarName()).isEqualTo(before.getCalendarName());
        assertThat(after.getPriority()).isEqualTo(before.getPriority());
        assertThat(after.getStartTime()).isEqualTo(before.getStartTime());
        assertThat(after.getNextFireTime()).isEqualTo(before.getNextFireTime());
        assertThat(after.getMisfireInstruction()).isEqualTo(before.getMisfireInstruction());
        assertThat(scheduler.getTriggerState(before.getKey())).isEqualTo(Trigger.TriggerState.PAUSED);
    }

    private void executeAndAssertData(JobKey key, JobDataMap expected) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<JobExecutionException> failure = new AtomicReference<>();
        AtomicReference<JobDataMap> actual = new AtomicReference<>();
        scheduler.getListenerManager().addJobListener(new JobListenerSupport() {
            @Override public String getName() { return "upgrade-execution"; }
            @Override public void jobWasExecuted(JobExecutionContext context, JobExecutionException exception) {
                actual.set(context.getJobDetail().getJobDataMap());
                failure.set(exception);
                completed.countDown();
            }
        }, KeyMatcher.keyEquals(key));
        scheduler.start();
        scheduler.triggerJob(key);
        assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
        assertThat(actual.get()).containsExactlyInAnyOrderEntriesOf(expected);
    }
}
