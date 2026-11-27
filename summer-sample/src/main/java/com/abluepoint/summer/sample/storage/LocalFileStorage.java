package com.abluepoint.summer.sample.storage;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.storage.FileStoreManager.Summary;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LocalFileStorage implements FileStorage {
    private final Path root;

    LocalFileStorage(@Value("${summer.storage.root:./data/file-repositories}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void prepare(FileRepositoryEntity repo) throws IOException {
        Files.createDirectories(root);
        Path directory = root.resolve(repo.path());
        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException exists) {
            if (!Files.isDirectory(directory, NOFOLLOW_LINKS))
                throw SummerException.withMessage(CommonErrorCode.INVALID_ARGUMENT, "本地位置必须是目录");
        }
    }

    private Path directory(FileRepositoryEntity repo) throws IOException {
        var dir = root.resolve(repo.path());
        if (!Files.isDirectory(dir, NOFOLLOW_LINKS)) throw new IOException("本地存储目录不可用");
        return dir;
    }

    private Path localFile(FileRepositoryEntity repo, String key, boolean createParents) throws IOException {
        Path parent = directory(repo);
        var parts = key.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            parent = parent.resolve(parts[i]);
            if (createParents && !Files.exists(parent, NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(parent);
                } catch (FileAlreadyExistsException concurrentWriter) {
                    // Check the winning directory below.
                }
            }
            if (!Files.isDirectory(parent, NOFOLLOW_LINKS)) throw new NoSuchFileException(parent.toString());
        }
        return parent.resolve(parts[parts.length - 1]);
    }

    @Override
    public void write(FileRepositoryEntity repo, String key, InputStream input, long size) throws IOException {
        store(localFile(repo, key, true), input, size, true);
    }

    @Override
    public void upload(FileRepositoryEntity repo, String key, InputStream input, long size) throws IOException {
        store(localFile(repo, key, false), input, size, false);
    }

    private void store(Path file, InputStream input, long size, boolean replace) throws IOException {
        var temp = Files.createTempFile(file.getParent(), ".upload-", ".tmp");
        try {
            if (Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING) != size)
                throw new IOException("文件长度不匹配");
            if (!replace) Files.createLink(file, temp);
            else Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream read(FileRepositoryEntity repo, String key) throws IOException {
        var file = localFile(repo, key, false);
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) throw new FileNotFoundException(key);
        return Files.newInputStream(file, NOFOLLOW_LINKS);
    }

    @Override
    public void deleteEmptyFolder(FileRepositoryEntity repo, String key) throws IOException {
        Path folder = localFile(repo, key, false);
        if (!Files.isDirectory(folder, NOFOLLOW_LINKS)) throw new NoSuchFileException(key);
        // The filesystem enforces emptiness, including hidden entries and in-flight uploads.
        Files.delete(folder);
    }

    @Override
    public void delete(FileRepositoryEntity repo, String key) throws IOException {
        Path file;
        try {
            file = localFile(repo, key, false);
        } catch (NoSuchFileException absent) {
            return;
        }
        if (Files.isDirectory(file, NOFOLLOW_LINKS))
            throw SummerException.withMessage(CommonErrorCode.INVALID_ARGUMENT, "对象名指向目录");
        Files.deleteIfExists(file);
    }

    @Override
    public java.util.List<FileStoreManager.Entry> entries(FileRepositoryEntity repo, String folder) throws IOException {
        var dir = folder.isEmpty() ? directory(repo) : localFile(repo, folder, false);
        if (!Files.isDirectory(dir, NOFOLLOW_LINKS)) throw new NoSuchFileException(folder);
        var entries = new java.util.ArrayList<FileStoreManager.Entry>();
        try (var children = Files.newDirectoryStream(dir)) {
            for (var child : children) {
                var name = child.getFileName().toString();
                if (name.startsWith(".upload-")) continue;
                var attrs = Files.readAttributes(child, BasicFileAttributes.class, NOFOLLOW_LINKS);
                if (attrs.isDirectory() || attrs.isRegularFile())
                    entries.add(new FileStoreManager.Entry(name, attrs.isDirectory(), attrs.size()));
            }
        }
        return entries;
    }

    @Override
    public void createFolder(FileRepositoryEntity repo, String key) throws IOException {
        Files.createDirectory(localFile(repo, key, false));
    }

    @Override
    public Summary summary(FileRepositoryEntity repo) {
        String location = root.resolve(repo.path()).toString();
        try {
            var dir = directory(repo);
            long available = Files.getFileStore(dir).getUsableSpace();
            long count = 0, bytes = 0;
            try (var files = Files.walk(dir)) {
                var iterator = files.iterator();
                while (iterator.hasNext()) {
                    var file = iterator.next();
                    if (file.getFileName().toString().startsWith(".upload-")) continue;
                    var attrs = Files.readAttributes(file, BasicFileAttributes.class, NOFOLLOW_LINKS);
                    if (attrs.isRegularFile()) {
                        count++;
                        bytes += attrs.size();
                    }
                }
            }
            return new Summary(repo.id(), repo.name(), repo.type(), location, "ONLINE", count, bytes, available);
        } catch (IOException | UncheckedIOException | SecurityException unavailable) {
            return new Summary(repo.id(), repo.name(), repo.type(), location, "OFFLINE", null, null, null);
        }
    }
}
