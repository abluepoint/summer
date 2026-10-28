package com.abluepoint.summer.sample.task.domain;
public interface TaskSchedulingPort {
 boolean validCron(String expression);
 void initializeRecovery();
 void scheduleExisting(TaskSchedule task);
 void schedule(TaskSchedule task);
 void reschedule(TaskSchedule task);
 void setStatus(TaskSchedule task, TaskStatus status);
 boolean dispatch(TaskSchedule task, String key);
}
