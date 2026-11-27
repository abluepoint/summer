package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.sample.storage.FileStoreManager;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.exception.SdkException;

@RestController
@RequestMapping("/api/v1/platform/file-repositories")
public class FileRepositoryController {
    private final FileStoreManager files;
    private final PermissionManagementGuard guard;

    public FileRepositoryController(FileStoreManager files, PermissionManagementGuard guard) {
        this.files = files;
        this.guard = guard;
    }

    public record Registration(String name, FileStoreManager.Type type, String path,
                               @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) String accessKeyId,
                               @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) String secretAccessKey) {
        @Override public String toString() { return "Registration[redacted]"; }
    }

    public record Credentials(String accessKeyId, String secretAccessKey) {
        @Override public String toString() { return "Credentials[redacted]"; }
    }

    @GetMapping
    public List<FileStoreManager.Summary> list(Authentication actor) {
        guard.require(Domain.platform(), actor, false);
        return files.list();
    }

    @Field(param = "name", style = "fileRepositoryNameStyle")
    @Field(param = "path", style = "fileRepositoryPathStyle")
    @Field(param = "type", style = "fileRepositoryTypeStyle")
    @Field(param = "accessKeyId", style = "s3AccessKeyIdStyle", required = false)
    @Field(param = "secretAccessKey", style = "s3SecretAccessKeyStyle", required = false)
    @PostMapping
    public FileStoreManager.Repository create(Authentication actor, @RequestBody Registration input) {
        guard.require(Domain.platform(), actor, true);
        try {
            return files.register(input.name(), input.type(), input.path(), input.accessKeyId(), input.secretAccessKey());
        } catch (IOException | SdkException unavailable) {
            throw SummerException.withMessage(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
                    "无法访问存储位置，请检查目录权限、存储桶、认证密钥及服务端配置");
        }
    }

    @Field(param = "id", style = "fileRepositoryIdStyle")
    @Field(param = "accessKeyId", style = "s3AccessKeyIdStyle")
    @Field(param = "secretAccessKey", style = "s3SecretAccessKeyStyle")
    @PutMapping("/{id}/credentials")
    public Map<String, Boolean> credentials(Authentication actor, @PathVariable String id, @RequestBody Credentials input) {
        guard.require(Domain.platform(), actor, true);
        try {
            files.updateCredentials(id, input.accessKeyId(), input.secretAccessKey());
            return Map.of("updated", true);
        } catch (IOException | SdkException unavailable) {
            throw SummerException.withMessage(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
                    "更新失败，请检查存储桶、认证密钥及服务端配置");
        }
    }

    @Field(param = "id", style = "fileRepositoryIdStyle")
    @DeleteMapping("/{id}")
    public Map<String, Boolean> remove(Authentication actor, @PathVariable String id) {
        guard.require(Domain.platform(), actor, true);
        files.unregister(id);
        return Map.of("removed", true);
    }
}
