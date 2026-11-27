package com.abluepoint.summer.sample.storage;

import com.abluepoint.summer.data.jpa.repository.BaseRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface FileEntryCreations extends BaseRepository<FileEntryCreation, Long> {
    List<FileEntryCreation> findByDirectory(byte[] directory);

    @Modifying
    @Query("delete from FileEntryCreation e where e.directory = :directory and e.name = :name and e.folder = :folder")
    void remove(byte[] directory, byte[] name, boolean folder);
}
