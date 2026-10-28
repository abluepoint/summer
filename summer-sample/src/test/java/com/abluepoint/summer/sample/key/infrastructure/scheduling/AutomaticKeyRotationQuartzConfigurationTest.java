package com.abluepoint.summer.sample.key.infrastructure.scheduling;

import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class AutomaticKeyRotationQuartzConfigurationTest {
    @Test void replacesPersistedImplementationWithoutChangingExistingTrigger() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.checkExists(new JobKey("automatic-key-rotation", "summer-security"))).thenReturn(true);
        new AutomaticKeyRotationQuartzConfiguration(scheduler).registerRotationJob();
        var job = org.mockito.ArgumentCaptor.forClass(JobDetail.class);
        verify(scheduler).addJob(job.capture(), eq(true), eq(true));
        assertThat(job.getValue().getJobClass()).isEqualTo(AutomaticKeyRotationJob.class);
        assertThat(job.getValue().getKey()).isEqualTo(new JobKey("automatic-key-rotation", "summer-security"));
        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(org.quartz.Trigger.class));
    }
}
