package com.abluepoint.summer.sample.storage;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.persistence.*;
import java.time.Instant;

/** Creation records follow the storage location across removal and re-registration. */
@Entity
@Table(name = "summer_file_entry_creation", uniqueConstraints =
        @UniqueConstraint(columnNames = {"directory_key", "entry_name", "is_folder"}))
class FileEntryCreation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "directory_key", nullable = false, length = 1160)
    private byte[] directory;

    @Column(name = "entry_name", nullable = false, length = 1024)
    private byte[] name;

    @Column(name = "is_folder", nullable = false)
    private boolean folder;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FileEntryCreation() {}

    FileEntryCreation(byte[] directory, byte[] name, boolean folder, String createdBy) {
        this.directory = directory;
        this.name = name;
        this.folder = folder;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    static byte[] directoryKey(FileRepositoryEntity repo, String parent) {
        return (repo.type().name() + "/" + repo.path() + "/" + parent).getBytes(UTF_8);
    }

    // S3 permits an object and a directory prefix with the same display name.
    String entryKey() { return new String(name, UTF_8) + (folder ? "/" : ""); }

    FileStoreManager.Entry describe(FileStoreManager.Entry entry) {
        return new FileStoreManager.Entry(entry.name(), entry.folder(), entry.size(), createdBy, createdAt);
    }
}
