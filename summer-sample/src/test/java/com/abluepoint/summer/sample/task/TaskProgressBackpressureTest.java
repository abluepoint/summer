package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskProgressBackpressureTest {
    @Test
    void staleCursorResynchronizesUsingABoundedDatabaseWindow() throws Exception {
        var definitions = mock(TaskDefinitionRepository.class);
        var events = mock(TaskProgressEventRepository.class);
        when(definitions.findById("task")).thenReturn(Optional.of(new TaskDefinitionEntity(
                "task", "ent-acme", "window", "system.noop", "0 0 0 1 1 ? 2099", "{}", 0)));
        var window = java.util.stream.LongStream.iterate(601, id -> id - 1).limit(501).mapToObj(id -> {
            var event = mock(TaskProgressEventEntity.class);
            when(event.eventId()).thenReturn(id);
            return event;
        }).toList();
        when(events.findTop501ByEnterpriseIdAndTaskIdOrderByEventIdDesc("ent-acme", "task")).thenReturn(window);
        try (var sockets = mockConstruction(SseEmitter.class)) {
            var service = new TaskProgressEventService(definitions, events, new ObjectMapper(), mock(TaskProgressAccess.class));
            try {
                service.subscribe("ent-acme", "task", 1, null);
                verify(sockets.constructed().get(0), times(2)).send(any(SseEmitter.SseEventBuilder.class));
                verify(events).findTop501ByEnterpriseIdAndTaskIdOrderByEventIdDesc("ent-acme", "task");
                verify(events, never()).findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc(anyString(), anyString());
            } finally { service.shutdown(); }
        }
    }

    @Test
    void completedStreamReleasesItsConnectionSlot() {
        var definitions = mock(TaskDefinitionRepository.class);
        var events = mock(TaskProgressEventRepository.class);
        when(definitions.findById("task")).thenReturn(Optional.of(new TaskDefinitionEntity(
                "task", "ent-acme", "capacity", "system.noop", "0 0 0 1 1 ? 2099", "{}", 0)));
        var completionCallbacks = new java.util.ArrayList<Runnable>();
        try (var sockets = mockConstruction(SseEmitter.class, (socket, context) ->
                doAnswer(invocation -> { completionCallbacks.add(invocation.getArgument(0)); return null; })
                        .when(socket).onCompletion(any(Runnable.class)))) {
            var service = new TaskProgressEventService(definitions, events, new ObjectMapper(), mock(TaskProgressAccess.class));
            try {
                for (int i = 0; i < 32; i++) service.subscribe("ent-acme", "task", 0, null);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.subscribe("ent-acme", "task", 0, null))
                        .isInstanceOfSatisfying(SummerException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.TASK_SSE_LIMIT));
                completionCallbacks.get(0).run();
                service.subscribe("ent-acme", "task", 0, null);
                assertThat(completionCallbacks).hasSize(33);
            } finally { service.shutdown(); }
        }
    }

    @Test
    void fourBlockedSubscribersCannotStarveAHealthySubscriber() throws Exception {
        var definitions = mock(TaskDefinitionRepository.class);
        var events = mock(TaskProgressEventRepository.class);
        when(definitions.findById("task")).thenReturn(Optional.of(new TaskDefinitionEntity(
                "task", "ent-acme", "delivery isolation", "system.noop", "0 0 0 1 1 ? 2099", "{}", 0)));
        var event = mock(TaskProgressEventEntity.class);
        when(event.eventId()).thenReturn(1L);
        when(event.taskId()).thenReturn("task");
        when(event.eventType()).thenReturn("execution.progress");
        when(event.payloadJson()).thenReturn("{}");
        when(events.findTop500ByEnterpriseIdAndTaskIdAndEventIdGreaterThanOrderByEventIdAsc(
                anyString(), anyString(), anyLong())).thenReturn(List.of(event));
        var blocked = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var healthy = new CountDownLatch(1);
        var created = new java.util.concurrent.atomic.AtomicInteger();
        try (var sockets = mockConstruction(SseEmitter.class, (socket, context) -> {
            boolean slow = created.incrementAndGet() <= 4;
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            doAnswer(invocation -> {
                if (calls.incrementAndGet() > 1) {
                    if (slow) {
                        blocked.countDown();
                        release.await(10, TimeUnit.SECONDS);
                    } else healthy.countDown();
                }
                return null;
            }).when(socket).send(any(SseEmitter.SseEventBuilder.class));
        })) {
            var service = new TaskProgressEventService(definitions, events, new ObjectMapper(), mock(TaskProgressAccess.class));
            try {
                for (int i = 0; i < 4; i++) service.subscribe("ent-acme", "task", 0, null);
                assertThat(blocked.await(3, TimeUnit.SECONDS)).isTrue();
                service.subscribe("ent-acme", "task", 0, null);
                assertThat(healthy.await(2, TimeUnit.SECONDS))
                        .as("healthy stream must receive progress while all four slow streams remain blocked").isTrue();
            } finally {
                release.countDown();
                service.shutdown();
            }
        }
    }

    @Test
    void slowSubscriberDoesNotBlockPersistingExecutionProgress() throws Exception {
        var definitions = mock(TaskDefinitionRepository.class);
        var events = mock(TaskProgressEventRepository.class);
        var task = new TaskDefinitionEntity("task", "ent-acme", "slow socket", "system.noop",
                "0 0 0 1 1 ? 2099", "{}", 0);
        when(definitions.findById("task")).thenReturn(Optional.of(task));
        when(events.findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc(anyString(), anyString())).thenReturn(List.of());
        var event = mock(TaskProgressEventEntity.class);
        when(event.eventId()).thenReturn(1L);
        when(event.taskId()).thenReturn("task");
        when(event.eventType()).thenReturn("execution.progress");
        when(event.payloadJson()).thenReturn("{}");
        when(events.findTop500ByEnterpriseIdAndTaskIdAndEventIdGreaterThanOrderByEventIdAsc(
                anyString(), anyString(), anyLong())).thenReturn(List.of(event));
        var sending = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var sockets = mockConstruction(SseEmitter.class, (socket, context) -> {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            doAnswer(invocation -> {
                if (calls.incrementAndGet() > 1) {
                    sending.countDown();
                    release.await(5, TimeUnit.SECONDS);
                }
                return null;
            }).when(socket).send(any(SseEmitter.SseEventBuilder.class));
        }); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var service = new TaskProgressEventService(definitions, events, new ObjectMapper(), mock(TaskProgressAccess.class));
            try {
                service.subscribe("ent-acme", "task", 0, null);
                assertThat(sending.await(3, TimeUnit.SECONDS)).isTrue();
                var execution = new TaskExecutionEntity("execution", "task", "ent-acme", "fire");
                var write = workers.submit(() -> service.started(execution));
                write.get(400, TimeUnit.MILLISECONDS);
                verify(events, times(2)).saveAndFlush(any());
            } finally {
                release.countDown();
                service.shutdown();
            }
        }
    }
}
