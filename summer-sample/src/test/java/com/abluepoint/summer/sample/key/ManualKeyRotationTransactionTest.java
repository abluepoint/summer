package com.abluepoint.summer.sample.key;

import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "summer.security.storage.keys=mysql")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ManualKeyRotationTransactionTest {
    private static final String MASTER = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void keyProperties(DynamicPropertyRegistry properties) {
        properties.add("summer.sample.security.master-key", () -> MASTER);
    }

    @Autowired MysqlSummerKeyStore store;
    @Autowired PlatformTransactionManager transactions;
    @Autowired KeyMaterialRepository materials;
    @Autowired KeyRotationStateRepository states;

    @Test
    void retryAfterRollbackSwitchesToTheKeyReportedByTheSuccessfulResponse() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        int version = store.currentVersion();
        String idempotencyKey = UUID.randomUUID().toString();
        String oldKid = signingKid();

        transaction.executeWithoutResult(status -> {
            store.rotate(1, version, idempotencyKey);
            status.setRollbackOnly();
        });
        assertThat(signingKid().equals(oldKid)).isTrue();

        MysqlSummerKeyStore.Rotation retry = store.rotate(1, version, idempotencyKey);
        assertThat(signingKid().equals(retry.activeKid())).isTrue();
        assertThat(signingKid().equals(oldKid)).isFalse();
    }

    @Test
    void independentNodesCompetingForOneVersionReturnOneSuccessAndOneConflict() throws Exception {
        MysqlSummerKeyStore first = new MysqlSummerKeyStore(materials, states, MASTER, 90, 30);
        MysqlSummerKeyStore second = new MysqlSummerKeyStore(materials, states, MASTER, 90, 30);
        int version = store.currentVersion();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> attempts = List.of(first, second).stream().map(node -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start_timeout");
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status ->
                            node.rotate(1, version, UUID.randomUUID().toString()));
                    return 200;
                } catch (com.abluepoint.summer.common.error.SummerException expected) {
                    return expected.errorCode() == com.abluepoint.summer.sample.error.SampleTransportErrorCode.PRECONDITION_FAILED ? 412 : 500;
                } catch (RuntimeException failure) {
                    return 500;
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(attempts.get(0).get(30, TimeUnit.SECONDS), attempts.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 412);
        }
        assertThat(store.metadata().stream().filter(key -> "ACTIVE".equals(key.get("status"))).count()).isEqualTo(1);
    }

    private String signingKid() throws Exception {
        return store.get(new JWKSelector(new JWKMatcher.Builder().keyType(KeyType.RSA).build()), null)
                .getFirst().getKeyID();
    }
}
