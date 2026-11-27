package com.abluepoint.summer.sample.storage;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "summer_file_repository", uniqueConstraints =
        @UniqueConstraint(columnNames = {"storage_type", "location"}))
class FileRepositoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "char(36)", length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "storage_type", nullable = false, length = 8)
    private FileStoreManager.Type type;

    @Column(nullable = false, length = 128)
    private String location;

    @Column(name = "s3_credentials", columnDefinition = "TEXT")
    private String encryptedCredentials;

    String id() { return id; }
    String name() { return name; }
    FileStoreManager.Type type() { return type; }
    String path() { return location; }
    String encryptedCredentials() { return encryptedCredentials; }
    void encryptedCredentials(String value) { encryptedCredentials = value; }

    protected FileRepositoryEntity() {}

    FileRepositoryEntity(String name, FileStoreManager.Type type, String location) {
        this.name = name;
        this.type = type;
        this.location = location;
    }

    FileStoreManager.Repository toRepository() {
        return new FileStoreManager.Repository(id, name, type, location);
    }
}
