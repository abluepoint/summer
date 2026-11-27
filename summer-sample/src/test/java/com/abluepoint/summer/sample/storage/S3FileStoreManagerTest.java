package com.abluepoint.summer.sample.storage;

import static org.assertj.core.api.Assertions.*;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/mysql",
        "spring.jpa.hibernate.ddl-auto=none"
})
@DirtiesContext
@Testcontainers(disabledWithoutDocker = true)
class S3FileStoreManagerTest {
    @Container @ServiceConnection static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");
    static final String user = "summer-test";
    static final String secret = UUID.randomUUID().toString();
    @Container static final GenericContainer<?> rustfs = new GenericContainer<>(
            "rustfs/rustfs@sha256:ee717072290bec9b11abfc0365f8310b00df1cd9b3639943edae47bdef05044a")
            .withEnv("RUSTFS_ACCESS_KEY", user).withEnv("RUSTFS_SECRET_KEY", secret)
            .withExposedPorts(9000)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/health/ready").forPort(9000));

    @TestConfiguration(proxyBeanMethods = false)
    static class Clients {
        @Bean @Primary
        S3Client testClient() {
            var endpoint = "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000);
            return StorageConfiguration.builder(endpoint, "us-east-1")
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(user, secret))).build();
        }
    }

    @org.springframework.test.context.DynamicPropertySource
    static void encryption(org.springframework.test.context.DynamicPropertyRegistry properties) {
        String masterKey = UUID.randomUUID().toString();
        properties.add("summer.sample.security.master-key", () -> masterKey);
    }
    @Autowired FileRepositoryRegistry registry;
    @Autowired FileStoreManager files;
    @Autowired S3Client client;

    @Test
    @Sql(statements = """
            insert into summer_file_repository(id,name,storage_type,location)
            values ('945bb83a-86e2-45dd-aa86-35c99cf98127','历史库','S3','legacy-archive');
            """)
    void managesExistingRegistration() {
        client.createBucket(b -> b.bucket("legacy-archive"));
        var existing = files.list().getFirst();
        assertThat(existing.id()).isEqualTo("945bb83a-86e2-45dd-aa86-35c99cf98127");
        assertThat(existing.type()).isEqualTo(FileStoreManager.Type.S3);
        assertThat(existing.path()).isEqualTo("s3://legacy-archive");
        assertThat(existing.state()).isEqualTo("ONLINE");
        assertThatThrownBy(() -> files.register("另一个名称", FileStoreManager.Type.S3, "legacy-archive"))
                .isInstanceOfSatisfying(SummerException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(CommonErrorCode.CONFLICT));
        files.unregister(existing.id());
    }

    @org.springframework.security.test.context.support.WithMockUser(username = "s3-uploader")
    @Test void signedObjectLifecycle() throws Exception {
        client.createBucket(b -> b.bucket("attachments"));
        var repo = files.register("附件", FileStoreManager.Type.S3, "attachments", user, secret);
        var original = registry.findById(repo.id()).orElseThrow().encryptedCredentials();
        assertThat(original != null && !original.contains(secret) && !original.contains(user)).isTrue();
        assertThatThrownBy(() -> files.updateCredentials(repo.id(), user, UUID.randomUUID().toString()))
                .isInstanceOf(software.amazon.awssdk.core.exception.SdkException.class);
        assertThat(original.equals(registry.findById(repo.id()).orElseThrow().encryptedCredentials())).isTrue();
        files.updateCredentials(repo.id(), user, secret);
        assertThat(original.equals(registry.findById(repo.id()).orElseThrow().encryptedCredentials())).isFalse();
        byte[] content = {4, 5, 6};
        files.createFolder(repo.id(), "", "archive");
        assertThat(files.entries(repo.id(), "")).extracting(FileStoreManager.Entry::name).containsExactly("archive");
        assertThat(files.entries(repo.id(), "archive")).isEmpty();
        files.upload(repo.id(), "archive", "样例", new ByteArrayInputStream(content), 3);
        var entry = files.entries(repo.id(), "archive").getFirst();
        assertThat(entry.name()).isEqualTo("样例");
        assertThat(entry.size()).isEqualTo(3);
        assertThat(entry.createdBy()).isEqualTo("s3-uploader");
        assertThat(entry.createdAt()).isNotNull();
        assertThat(files.entries(repo.id(), "").getFirst().createdBy()).isEqualTo("s3-uploader");
        try (var input = files.read(repo.id(), "archive/样例")) { assertThat(input.readAllBytes()).isEqualTo(content); }
        var summary = files.list().getFirst();
        assertThat(summary.totalSize()).isEqualTo(3L);
        assertThat(summary.blobCount()).isEqualTo(1L);
        assertThat(summary.availableSpace()).isNull();
        assertThatThrownBy(() -> files.deleteEmptyFolder(repo.id(), "archive"))
                .isInstanceOf(java.nio.file.DirectoryNotEmptyException.class);
        files.delete(repo.id(), "archive/样例");
        files.createFolder(repo.id(), "archive", "nested");
        assertThatThrownBy(() -> files.deleteEmptyFolder(repo.id(), "archive"))
                .isInstanceOf(java.nio.file.DirectoryNotEmptyException.class);
        files.deleteEmptyFolder(repo.id(), "archive/nested");
        files.deleteEmptyFolder(repo.id(), "archive");
        assertThat(files.entries(repo.id(), "")).isEmpty();
        assertThat(files.list().getFirst().blobCount()).isZero();
        files.unregister(repo.id());
        assertThat(files.list()).isEmpty();
    }
}
