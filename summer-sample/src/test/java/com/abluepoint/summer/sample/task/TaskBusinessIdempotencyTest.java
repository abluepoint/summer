package com.abluepoint.summer.sample.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.quartz.job-store-type=memory", "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes"},
        classes = {com.abluepoint.summer.sample.SummerSampleApplication.class, TaskBusinessIdempotencyTest.Business.class})
class TaskBusinessIdempotencyTest {
    @Autowired TaskExecutionService service;
    @Autowired TaskDefinitionRepository definitions;
    @Autowired TaskExecutionRepository executions;
    @Autowired TaskAttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CountedHandler handler;

    @BeforeEach void tables() {
        jdbc.execute("create table if not exists acceptance_task_business_receipt (tenant varchar(80), task_id varchar(80), business_key varchar(255), primary key(tenant,task_id,business_key))");
        jdbc.execute("create table if not exists acceptance_task_business_counter (task_id varchar(80) primary key, effects integer not null)");
        handler.calls.set(0); handler.failAfterCommit = false;
    }

    @Test void retryAfterCommittedBusinessWriteDoesNotRepeatTheSideEffect() {
        String id=task(1); handler.failAfterCommit=true;
        service.execute(id,"fire","retry-key",Instant.now(),false);
        assertThat(handler.calls).hasValue(2);
        assertThat(effects(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from acceptance_task_business_receipt where tenant=? and task_id=? and business_key=?",
                Integer.class,"ent-acme",id,"retry-key")).isEqualTo(1);
        var rows=executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc("ent-acme",id);
        assertThat(rows).hasSize(1); assertThat(rows.getFirst().status()).isEqualTo(TaskExecutionStatus.SUCCESS);
        assertThat(attempts.findAllByExecutionIdOrderByAttemptAsc(rows.getFirst().executionId())).hasSize(2);
    }

    @Test void recoveryReplaysTheOriginalBusinessKeyAfterTheOldNodeCommittedItsEffect() {
        String id=task(0);String key="node-lost-key";
        handler.execute(Map.of("_summerTaskEnterpriseId","ent-acme","_summerTaskId",id,"_summerTaskIdempotencyKey",key),ignored->{});
        executions.saveAndFlush(new TaskExecutionEntity(UUID.randomUUID().toString(),id,"ent-acme","lost-fire",key,"lost-node",Instant.now().minusSeconds(30)));
        service.execute(id,"recovery-fire",key,Instant.now(),true);
        assertThat(handler.calls).hasValue(2); assertThat(effects(id)).isEqualTo(1);
        assertThat(executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc("ent-acme",id).getFirst().status()).isEqualTo(TaskExecutionStatus.SUCCESS);
    }

    @Test void competingDispatchesCannotRepeatTheCountedBusinessWrite() throws Exception {
        String id=task(0);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.execute(id,"fire-a","same-key",Instant.now(),false));
            var b=pool.submit(()->service.execute(id,"fire-b","same-key",Instant.now(),false));
            a.get(10,java.util.concurrent.TimeUnit.SECONDS);b.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(effects(id)).isEqualTo(1);
        assertThat(executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc("ent-acme",id)).hasSize(1);
    }

    private String task(int retries) {
        String id=UUID.randomUUID().toString();jdbc.update("insert into acceptance_task_business_counter values (?,0)",id);
        definitions.saveAndFlush(new TaskDefinitionEntity(id,"ent-acme","Counted side effect "+id,"acceptance.counted",
                "0 0 0 1 1 ? 2099","{\"_summerTaskEnterpriseId\":\"spoofed\",\"_summerTaskId\":\"spoofed\",\"_summerTaskIdempotencyKey\":\"spoofed\"}",retries,10000));return id;
    }
    private int effects(String task) { return jdbc.queryForObject("select effects from acceptance_task_business_counter where task_id=?",Integer.class,task); }

    @TestConfiguration static class Business {
        @Bean CountedHandler countedHandler(JdbcTemplate jdbc,PlatformTransactionManager manager) {return new CountedHandler(jdbc,manager);}
    }
    static class CountedHandler implements RegisteredTaskHandler {
        final JdbcTemplate jdbc;final TransactionTemplate transaction;final AtomicInteger calls=new AtomicInteger();boolean failAfterCommit;
        CountedHandler(JdbcTemplate jdbc,PlatformTransactionManager manager) {this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);}
        @Override public String taskType() {return "acceptance.counted";}
        @Override public void execute(Map<String,Object> parameters,IntConsumer progress) {
            int call=calls.incrementAndGet();String id=(String)parameters.get("_summerTaskId");
            try {transaction.executeWithoutResult(ignored->{
                jdbc.update("insert into acceptance_task_business_receipt values (?,?,?)",parameters.get("_summerTaskEnterpriseId"),id,parameters.get("_summerTaskIdempotencyKey"));
                assertThat(jdbc.update("update acceptance_task_business_counter set effects=effects+1 where task_id=?",id)).isEqualTo(1);
            });} catch(DuplicateKeyException alreadyCommitted) { /* One transaction owns the business effect. */ }
            if(failAfterCommit && call==1) throw new IllegalStateException("simulated loss after commit");
        }
    }
}
