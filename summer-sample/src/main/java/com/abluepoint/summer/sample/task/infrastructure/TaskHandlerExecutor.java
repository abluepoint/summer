package com.abluepoint.summer.sample.task.infrastructure;
import java.util.Map;
import java.util.concurrent.*;
import com.abluepoint.summer.sample.task.domain.TaskTimedOutException;
@org.springframework.stereotype.Component
public final class TaskHandlerExecutor {
 private final TrustedTaskRegistry registry;
 public TaskHandlerExecutor(TrustedTaskRegistry registry) { this.registry=registry; }
    private static final ExecutorService HANDLERS = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "summer-task-handler");
        thread.setDaemon(true);
        return thread;
    });
    public void execute(String taskType, long timeoutMs, Map<String, Object> parameters, java.util.function.IntConsumer progressReporter) {
        Future<?> future = HANDLERS.submit(() -> registry.execute(taskType, parameters, progressReporter));
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw new TaskTimedOutException();
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new TaskTimedOutException();
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException(cause);
        }
    }


}
