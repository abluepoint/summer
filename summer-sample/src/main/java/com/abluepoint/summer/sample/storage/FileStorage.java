package com.abluepoint.summer.sample.storage;

import com.abluepoint.summer.sample.storage.FileStoreManager.Summary;
import java.io.IOException;
import java.io.InputStream;

/** Physical operations on the location recorded for a repository. */
interface FileStorage {
    void prepare(FileRepositoryEntity repo) throws IOException;

    void write(FileRepositoryEntity repo, String key, InputStream input, long size) throws IOException;

    InputStream read(FileRepositoryEntity repo, String key) throws IOException;

    void deleteEmptyFolder(FileRepositoryEntity repo, String key) throws IOException;

    void delete(FileRepositoryEntity repo, String key) throws IOException;

    java.util.List<FileStoreManager.Entry> entries(FileRepositoryEntity repo, String folder) throws IOException;
    void createFolder(FileRepositoryEntity repo, String key) throws IOException;
    void upload(FileRepositoryEntity repo, String key, InputStream input, long size) throws IOException;

    Summary summary(FileRepositoryEntity repo);
}
