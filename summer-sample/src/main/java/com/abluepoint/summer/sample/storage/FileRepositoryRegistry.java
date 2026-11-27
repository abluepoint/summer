package com.abluepoint.summer.sample.storage;

import com.abluepoint.summer.data.jpa.repository.BaseRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface FileRepositoryRegistry extends BaseRepository<FileRepositoryEntity, String> {
    List<FileRepositoryEntity> findAllByOrderByNameAscIdAsc();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FileRepositoryEntity r where r.id = :id")
    java.util.Optional<FileRepositoryEntity> lockRepository(String id);

    @Modifying
    @Query("delete from FileRepositoryEntity r where r.id = :id")
    int removeRegistration(String id);
}
