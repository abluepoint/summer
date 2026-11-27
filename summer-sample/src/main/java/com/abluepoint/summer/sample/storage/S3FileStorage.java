package com.abluepoint.summer.sample.storage;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.storage.FileStoreManager.Summary;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@Component
class S3FileStorage implements FileStorage {
    private final ObjectProvider<S3Client> clients;

    private final String masterKey;
    private final ObjectMapper json;

    S3FileStorage(ObjectProvider<S3Client> clients, ObjectMapper json,
                  @Value("${SUMMER_SECURITY_MASTER_KEY:${summer.sample.security.master-key:}}") String masterKey) {
        this.masterKey = masterKey;
        this.json = json;
        this.clients = clients;
    }

    private TextEncryptor encryptor(FileRepositoryEntity repo) throws IOException {
        if (masterKey.isBlank()) throw new IOException("服务端未配置存储凭据加密主密钥");
        return Encryptors.delux(masterKey, repo.id().replace("-", ""));
    }

    void setCredentials(FileRepositoryEntity repo, String id, String secret) throws IOException {
        if (id == null || id.isBlank() || id.length() > 256 || secret == null || secret.isBlank() || secret.length() > 512)
            throw SummerException.withMessage(CommonErrorCode.INVALID_ARGUMENT, "请填写完整的 S3 认证密钥");
        repo.encryptedCredentials(encryptor(repo).encrypt(json.writeValueAsString(List.of(id, secret))));
    }

    private AwsRequestOverrideConfiguration authentication(FileRepositoryEntity repo) throws IOException {
        var config = AwsRequestOverrideConfiguration.builder();
        if (repo.encryptedCredentials() != null) {
            try {
                var pair = json.readValue(encryptor(repo).decrypt(repo.encryptedCredentials()), String[].class);
                config.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(pair[0], pair[1])));
            } catch (RuntimeException invalid) {
                throw new IOException("存储库凭据无法解密");
            }
        }
        return config.build();
    }

    @Override
    public void prepare(FileRepositoryEntity repo) throws IOException {
        String bucket = repo.path();
        if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") || bucket.contains(".."))
            throw SummerException.withMessage(CommonErrorCode.INVALID_ARGUMENT, "请输入有效的 S3 存储桶名称");
        var auth = authentication(repo);
        clients.getObject().headBucket(b -> b.overrideConfiguration(auth).bucket(bucket));
    }

    @Override
    public void write(FileRepositoryEntity repo, String key, InputStream input, long size) throws IOException {
        var auth = authentication(repo);
        clients.getObject().putObject(b -> b.overrideConfiguration(auth).bucket(repo.path()).key(key), RequestBody.fromInputStream(input, size));
    }

    @Override
    public InputStream read(FileRepositoryEntity repo, String key) throws IOException {
        var auth = authentication(repo);
        return clients.getObject().getObject(b -> b.overrideConfiguration(auth).bucket(repo.path()).key(key));
    }

    @Override
    public void deleteEmptyFolder(FileRepositoryEntity repo, String key) throws IOException {
        String marker = key + "/";
        var auth = authentication(repo);
        var client = clients.getObject();
        var page = client.listObjectsV2(b -> b.overrideConfiguration(auth).bucket(repo.path()).prefix(marker).maxKeys(2));
        if (page.contents().isEmpty()) throw new java.nio.file.NoSuchFileException(key);
        if (Boolean.TRUE.equals(page.isTruncated()) || page.contents().size() != 1
                || !marker.equals(page.contents().getFirst().key()) || page.contents().getFirst().size() != 0)
            throw new java.nio.file.DirectoryNotEmptyException(key);
        client.deleteObject(b -> b.overrideConfiguration(auth).bucket(repo.path()).key(marker));
    }

    @Override
    public void delete(FileRepositoryEntity repo, String key) throws IOException {
        var auth = authentication(repo);
        clients.getObject().deleteObject(b -> b.overrideConfiguration(auth).bucket(repo.path()).key(key));
    }

    @Override
    public List<FileStoreManager.Entry> entries(FileRepositoryEntity repo, String folder) throws IOException {
        var prefix = folder.isEmpty() ? "" : folder + "/";
        var auth = authentication(repo);
        var pages = clients.getObject().listObjectsV2Paginator(b -> b.overrideConfiguration(auth)
                .bucket(repo.path()).prefix(prefix).delimiter("/"));
        var entries = new java.util.ArrayList<FileStoreManager.Entry>();
        boolean exists = folder.isEmpty();
        for (var page : pages) {
            for (var child : page.commonPrefixes()) {
                exists = true;
                var key = child.prefix();
                entries.add(new FileStoreManager.Entry(key.substring(prefix.length(), key.length() - 1), true, 0));
            }
            for (var object : page.contents()) {
                exists = true;
                if (!object.key().equals(prefix))
                    entries.add(new FileStoreManager.Entry(object.key().substring(prefix.length()), false, object.size()));
            }
        }
        if (!exists) throw new java.nio.file.NoSuchFileException(folder);
        return entries;
    }

    @Override
    public void createFolder(FileRepositoryEntity repo, String key) throws IOException {
        var auth = authentication(repo);
        clients.getObject().putObject(b -> b.overrideConfiguration(auth).bucket(repo.path()).key(key + "/")
                .ifNoneMatch("*"), RequestBody.empty());
    }

    @Override
    public void upload(FileRepositoryEntity repo, String key, InputStream input, long size) throws IOException {
        var auth = authentication(repo);
        clients.getObject().putObject(b -> b.overrideConfiguration(auth).bucket(repo.path()).key(key)
                .ifNoneMatch("*"), RequestBody.fromInputStream(input, size));
    }

    @Override
    public Summary summary(FileRepositoryEntity repo) {
        String location = "s3://" + repo.path();
        try {
            long count = 0, bytes = 0;
            var auth = authentication(repo);
            var pages = clients.getObject().listObjectsV2Paginator(b -> b.overrideConfiguration(auth).bucket(repo.path()));
            for (var object : pages.contents()) {
                if (object.key().endsWith("/")) continue;
                count++;
                bytes += object.size();
            }
            return new Summary(repo.id(), repo.name(), repo.type(), location, "ONLINE", count, bytes, null);
        } catch (IOException | SdkException | SecurityException unavailable) {
            return new Summary(repo.id(), repo.name(), repo.type(), location, "OFFLINE", null, null, null);
        }
    }
}
