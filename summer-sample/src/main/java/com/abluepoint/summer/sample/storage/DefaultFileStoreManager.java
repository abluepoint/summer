package com.abluepoint.summer.sample.storage;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import static java.nio.charset.StandardCharsets.UTF_8;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("fileStoreManager")
public class DefaultFileStoreManager implements FileStoreManager {
    private final FileRepositoryRegistry registry;
    private final FileEntryCreations creations;
    private final LocalFileStorage local;
    private final S3FileStorage s3;

    DefaultFileStoreManager(FileRepositoryRegistry registry, FileEntryCreations creations, LocalFileStorage local, S3FileStorage s3) {
        this.registry = registry;
        this.creations = creations;
        this.local = local;
        this.s3 = s3;
    }

    private FileStorage storage(Type type) {
        return switch (type) {
            case LOCAL -> local;
            case S3 -> s3;
        };
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public Repository register(String name, Type type, String path) throws IOException {
        return register(name, type, path, null, null);
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public Repository register(String name, Type type, String path, String accessKeyId, String secretAccessKey) throws IOException {
        if (name == null || name.isBlank() || name.strip().length() > 100 || type == null || path == null
                || !path.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}"))
            throw invalid("请输入有效的名称和存储位置");
        boolean hasCredentials = accessKeyId != null || secretAccessKey != null;
        if (hasCredentials && type != Type.S3) throw invalid("仅 S3 存储库支持认证密钥");
        FileRepositoryEntity repo;
        try {
            repo = registry.saveAndFlush(new FileRepositoryEntity(name.strip(), type, path));
        } catch (DataIntegrityViolationException failure) {
            if (failure.getCause() instanceof ConstraintViolationException constraint
                    && constraint.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE)
                throw SummerException.withMessage(CommonErrorCode.CONFLICT, "名称或存储位置已注册");
            throw failure;
        }
        if (hasCredentials) s3.setCredentials(repo, accessKeyId, secretAccessKey);
        storage(type).prepare(repo);
        return repo.toRepository();
    }

    @Override
    @Transactional
    public void unregister(String id) {
        if (registry.removeRegistration(id) == 0) throw missing();
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public void updateCredentials(String id, String accessKeyId, String secretAccessKey) throws IOException {
        var repo = repository(id);
        if (repo.type() != Type.S3) throw invalid("仅 S3 存储库支持认证密钥");
        s3.setCredentials(repo, accessKeyId, secretAccessKey);
        s3.prepare(repo);
    }

    private FileRepositoryEntity repository(String id) {
        return registry.findById(id).orElseThrow(DefaultFileStoreManager::missing);
    }

    private static SummerException missing() {
        return SummerException.withMessage(CommonErrorCode.NOT_FOUND, "文件存储库不存在");
    }

    private static SummerException invalid(String message) {
        return SummerException.withMessage(CommonErrorCode.INVALID_ARGUMENT, message);
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024
                || key.contains("\\") || key.contains(":") || key.chars().anyMatch(Character::isISOControl))
            throw invalid("对象名必须是有效的相对路径");
        for (String part : key.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..") || part.startsWith(".upload-"))
                throw invalid("对象路径包含无效的文件名");
        }
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public void write(String id, String key, InputStream input, long size) throws IOException {
        validateKey(key);
        if (size < 0) throw invalid("文件大小不能为负数");
        var repo = registry.lockRepository(id).orElseThrow(DefaultFileStoreManager::missing);
        storage(repo.type()).write(repo, key, input, size);
    }

    @Override
    public InputStream read(String id, String key) throws IOException {
        validateKey(key);
        var repo = repository(id);
        return storage(repo.type()).read(repo, key);
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public void delete(String id, String key) throws IOException {
        deleteEntry(id, key, false);
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public void deleteEmptyFolder(String id, String key) throws IOException {
        deleteEntry(id, key, true);
    }

    private void deleteEntry(String id, String key, boolean folder) throws IOException {
        validateKey(key);
        var repo = registry.lockRepository(id).orElseThrow(DefaultFileStoreManager::missing);
        int slash = key.lastIndexOf('/');
        String parent = slash < 0 ? "" : key.substring(0, slash);
        creations.remove(FileEntryCreation.directoryKey(repo, parent), key.substring(slash + 1).getBytes(UTF_8), folder);
        if (folder) storage(repo.type()).deleteEmptyFolder(repo, key);
        else storage(repo.type()).delete(repo, key);
    }

    @Override
    public List<Choice> choices() {
        return registry.findAllByOrderByNameAscIdAsc().stream().map(r -> new Choice(r.id(), r.name())).toList();
    }

    @Override
    public List<Entry> entries(String id, String folder) throws IOException {
        if (!folder.isEmpty()) validateKey(folder);
        var repo = repository(id);
        var entries = storage(repo.type()).entries(repo, folder);
        var records = new HashMap<String, FileEntryCreation>();
        for (var record : creations.findByDirectory(FileEntryCreation.directoryKey(repo, folder)))
            records.put(record.entryKey(), record);
        return entries.stream().map(entry -> {
            var record = records.get(entry.name() + (entry.folder() ? "/" : ""));
            return record == null ? entry : record.describe(entry);
        }).sorted(java.util.Comparator.comparing(Entry::folder).reversed().thenComparing(Entry::name)).toList();
    }

    private void recordCreation(FileRepositoryEntity repo, String folder, String name, boolean isFolder) {
        var actor = SecurityContextHolder.getContext().getAuthentication();
        String creator = actor != null && actor.isAuthenticated() && !(actor instanceof AnonymousAuthenticationToken)
                ? actor.getName() : null;
        var directory = FileEntryCreation.directoryKey(repo, folder);
        var entryName = name.getBytes(UTF_8);
        // Physical storage may have been cleaned up outside the platform.
        creations.remove(directory, entryName, isFolder);
        creations.saveAndFlush(new FileEntryCreation(directory, entryName, isFolder, creator));
    }

    private String newKey(FileRepositoryEntity repo, String folder, String name) throws IOException {
        if (name == null || name.contains("/")) throw invalid("请输入单个文件或文件夹名称");
        if (!folder.isEmpty()) validateKey(folder);
        var key = folder.isEmpty() ? name : folder + "/" + name;
        validateKey(key);
        if (storage(repo.type()).entries(repo, folder).stream().anyMatch(entry -> entry.name().equals(name)))
            throw SummerException.withMessage(CommonErrorCode.CONFLICT, "已存在同名文件或文件夹");
        return key;
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public void createFolder(String id, String folder, String name) throws IOException {
        var repo = registry.lockRepository(id).orElseThrow(DefaultFileStoreManager::missing);
        var key = newKey(repo, folder, name);
        validateKey(key + "/x"); // A folder must be able to contain a child key.
        recordCreation(repo, folder, name, true);
        storage(repo.type()).createFolder(repo, key);
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public void upload(String id, String folder, String name, InputStream input, long size) throws IOException {
        var repo = registry.lockRepository(id).orElseThrow(DefaultFileStoreManager::missing);
        var key = newKey(repo, folder, name);
        recordCreation(repo, folder, name, false);
        storage(repo.type()).upload(repo, key, input, size);
    }

    @Override
    public List<Summary> list() {
        return registry.findAllByOrderByNameAscIdAsc().stream()
                .map(repo -> storage(repo.type()).summary(repo))
                .toList();
    }
}
