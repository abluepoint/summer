package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.sample.storage.FileStoreManager;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RestController
@RequestMapping("/api/v1/platform/files")
public class PlatformFileController {
    private final FileStoreManager files;
    private final PermissionManagementGuard guard;

    public PlatformFileController(FileStoreManager files, PermissionManagementGuard guard) {
        this.files = files;
        this.guard = guard;
    }

    @GetMapping
    public List<FileStoreManager.Choice> repositories(Authentication actor) {
        guard.require(Domain.platform(), actor, false);
        return files.choices();
    }

    @Field(param = "id", style = "fileRepositoryIdStyle")
    @Field(param = "folder", style = "fileKeyStyle", required = false)
    @GetMapping("/{id}/entries")
    public List<FileStoreManager.Entry> entries(Authentication actor, @PathVariable String id,
                                                @RequestParam(defaultValue = "") String folder) throws IOException {
        guard.require(Domain.platform(), actor, false);
        return access(() -> files.entries(id, folder));
    }

    @Field(param = "id", style = "fileRepositoryIdStyle")
    @Field(param = "folder", style = "fileKeyStyle", required = false)
    @Field(param = "name", style = "fileKeyStyle")
    @PostMapping("/{id}/folders")
    public Map<String, Boolean> createFolder(Authentication actor, @PathVariable String id,
            @RequestParam(defaultValue = "") String folder, @RequestParam String name) throws IOException {
        guard.require(Domain.platform(), actor, true);
        return access(() -> {
            files.createFolder(id, folder, name);
            return Map.of("created", true);
        });
    }

    @Field(param = "id", style = "fileRepositoryIdStyle")
    @Field(param = "folder", style = "fileKeyStyle", required = false)
    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Boolean> upload(Authentication actor, @PathVariable String id,
            @RequestParam(defaultValue = "") String folder, @RequestPart MultipartFile file) throws IOException {
        guard.require(Domain.platform(), actor, true);
        try (var input = file.getInputStream()) {
            return access(() -> {
                files.upload(id, folder, file.getOriginalFilename(), input, file.getSize());
                return Map.of("uploaded", true);
            });
        }
    }

    @Field(param = "id", style = "fileRepositoryIdStyle")
    @Field(param = "key", style = "fileKeyStyle")
    @GetMapping("/{id}/download")
    @RawResponse
    public void download(Authentication actor, @PathVariable String id, @RequestParam String key,
                         HttpServletResponse response) throws IOException {
        guard.require(Domain.platform(), actor, false);
        try (var input = access(() -> files.read(id, key))) {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(key.substring(key.lastIndexOf('/') + 1), StandardCharsets.UTF_8).build().toString());
            input.transferTo(response.getOutputStream());
        }
    }

    @Field(param = "id", style = "fileRepositoryIdStyle")
    @Field(param = "key", style = "fileKeyStyle")
    @DeleteMapping("/{id}/entries")
    public Map<String, Boolean> deleteFile(Authentication actor, @PathVariable String id, @RequestParam String key) {
        guard.require(Domain.platform(), actor, true);
        return access(() -> {
            // A trailing slash explicitly addresses the folder, including on S3 where
            // an object and a prefix may share the same display name.
            if (key.endsWith("/")) files.deleteEmptyFolder(id, key.substring(0, key.length() - 1));
            else files.delete(id, key);
            return Map.of("deleted", true);
        });
    }

    private interface StorageCall<T> { T run() throws IOException; }

    private static <T> T access(StorageCall<T> call) {
        try {
            return call.run();
        } catch (IOException | SdkException failure) {
            throw storageError(failure);
        }
    }

    private static SummerException storageError(Exception failure) {
        if (failure instanceof java.nio.file.DirectoryNotEmptyException)
            return SummerException.withMessage(CommonErrorCode.CONFLICT, "文件夹非空，请先清空文件夹");
        if (failure instanceof NoSuchFileException || failure instanceof FileNotFoundException
                || failure instanceof S3Exception s3 && s3.statusCode() == 404)
            return SummerException.withMessage(CommonErrorCode.NOT_FOUND, "文件或文件夹不存在");
        if (failure instanceof FileAlreadyExistsException || failure instanceof S3Exception s3 && s3.statusCode() == 412)
            return SummerException.withMessage(CommonErrorCode.CONFLICT, "已存在同名文件或文件夹");
        return SummerException.withMessage(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE, "无法访问文件存储库");
    }
}
