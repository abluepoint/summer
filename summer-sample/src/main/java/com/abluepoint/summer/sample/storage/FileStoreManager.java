package com.abluepoint.summer.sample.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.time.Instant;

/** Repository IDs address registered locations. Callers own input and returned streams. */
public interface FileStoreManager {
    enum Type { LOCAL, S3 }
    record Repository(String id, String name, Type type, String path) {}
    record Summary(String id, String name, Type type, String path, String state,
                          Long blobCount, Long totalSize, Long availableSpace) {}

    record Entry(String name, boolean folder, long size, String createdBy, Instant createdAt) {
        public Entry(String name, boolean folder, long size) {
            this(name, folder, size, null, null);
        }
    }
    record Choice(String id, String name) {}

    List<Choice> choices();
    List<Entry> entries(String id, String folder) throws IOException;
    void createFolder(String id, String folder, String name) throws IOException;
    void upload(String id, String folder, String name, InputStream input, long size) throws IOException;

    Repository register(String name, Type type, String path) throws IOException;

    Repository register(String name, Type type, String path, String accessKeyId, String secretAccessKey) throws IOException;

    void updateCredentials(String id, String accessKeyId, String secretAccessKey) throws IOException;

    /** Removes the registration only. Existing objects survive re-registration. */
    void unregister(String id);

    List<Summary> list();

    void write(String id, String key, InputStream input, long size) throws IOException;

    InputStream read(String id, String key) throws IOException;

    void deleteEmptyFolder(String id, String key) throws IOException;

    void delete(String id, String key) throws IOException;
}
