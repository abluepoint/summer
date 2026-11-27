package com.abluepoint.summer.sample.authorization.api.permission;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.abluepoint.summer.sample.authorization.application.permission.*;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.*;
import com.abluepoint.summer.sample.storage.FileStoreManager;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class FileRepositoryHttpTest {
    @TempDir static Path root;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("summer.storage.root", () -> root.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ManagedInterfaceResources resources;
    @Autowired PermissionResourceAssociations associations;
    @Autowired MenuPublicationState publication;
    @Autowired PlatformTransactionManager transactions;
    @Autowired FileStoreManager files;
    static final String API = "/api/v1/platform/file-repositories";

    @Test void registrationFileLifecycleAndNavigation() throws Exception {
        db.execute("create table summer_file_repository_install (id int primary key)");
        db.update("update summer_menu_publication set published=true where publication_id='global'");
        var installer = new PlatformDirectoryInstallation(db, resources, associations, publication);
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(t -> installer.installFileRepositories());
        tx.executeWithoutResult(t -> installer.installFileRepositories());
        tx.executeWithoutResult(t -> installer.installFiles());
        tx.executeWithoutResult(t -> installer.installFiles());
        var admin = user("summer-user");
        mvc.perform(get("/api/v1/platform/authorization/navigation").with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[?(@.menuId == 'platform/file-repositories')].parentMenuId")
                        .value(org.hamcrest.Matchers.hasItem("platform-management-files")));
        mvc.perform(get(API).with(user("unassigned"))).andExpect(status().isForbidden());
        mvc.perform(post(API).with(admin).with(csrf()).contentType("application/json")
                .content("{\"name\":\"附件\",\"type\":\"LOCAL\",\"path\":\"attachments\"}")).andExpect(status().isOk());
        var id = files.list().getFirst().id();
        mvc.perform(post(API).with(admin).with(csrf()).contentType("application/json")
                .content("{\"name\":\"附件\",\"type\":\"LOCAL\",\"path\":\"unused\"}")).andExpect(status().isConflict());
        assertThat(root.resolve("unused")).doesNotExist();
        Files.createSymbolicLink(root.resolve("alias"), root.resolve("attachments"));
        mvc.perform(post(API).with(admin).with(csrf()).contentType("application/json")
                .content("{\"name\":\"别名\",\"type\":\"LOCAL\",\"path\":\"alias\"}")).andExpect(status().isBadRequest());
        assertThat(files.list()).hasSize(1);
        byte[] content = {1,2,3};
        String filesApi = "/api/v1/platform/files/" + id;
        mvc.perform(get("/api/v1/platform/authorization/navigation").with(admin))
                .andExpect(jsonPath("$.data.items[?(@.menuId == 'platform/files')].parentMenuId")
                        .value(org.hamcrest.Matchers.hasItem("platform-management-files")));
        mvc.perform(get(filesApi + "/entries").with(user("unassigned"))).andExpect(status().isForbidden());
        mvc.perform(post(filesApi + "/folders").param("name", "子目录").with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get(filesApi + "/entries").with(admin)).andExpect(jsonPath("$.data[0].folder").value(true))
                .andExpect(jsonPath("$.data[0].createdBy").value("summer-user"))
                .andExpect(jsonPath("$.data[0].createdAt").isString());
        var part = new org.springframework.mock.web.MockMultipartFile("file", "样例.bin", "application/octet-stream", content);
        mvc.perform(multipart(filesApi + "/upload").file(part).param("folder","子目录").with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(multipart(filesApi + "/upload").file(part).param("folder","子目录").with(admin).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(get(filesApi + "/entries").param("folder", "子目录").with(admin))
                .andExpect(jsonPath("$.data[0].createdBy").value("summer-user"))
                .andExpect(jsonPath("$.data[0].createdAt").isString());
        mvc.perform(get(filesApi + "/download").param("key","子目录/样例.bin").with(admin))
                .andExpect(status().isOk()).andExpect(content().bytes(content));
        mvc.perform(get(filesApi + "/download").param("key","../非法").with(admin)).andExpect(status().isBadRequest());
        mvc.perform(get(filesApi + "/entries").param("folder","缺失").with(admin)).andExpect(status().isNotFound());
        mvc.perform(delete(filesApi + "/entries").param("key","子目录/样例.bin").with(user("unassigned")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(filesApi + "/entries").param("key","子目录/").with(admin).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value("文件夹非空，请先清空文件夹"));
        assertThat(root.resolve("attachments/子目录/样例.bin")).exists();
        mvc.perform(delete(filesApi + "/entries").param("key","子目录/样例.bin").with(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deleted").value(true));
        mvc.perform(get(filesApi + "/download").param("key","子目录/样例.bin").with(admin)).andExpect(status().isNotFound());
        mvc.perform(delete(filesApi + "/entries").param("key","子目录").with(admin).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete(filesApi + "/entries").param("key","../outside").with(admin).with(csrf())).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> files.upload(id, "子目录", "失败", new ByteArrayInputStream(content), 4))
                .isInstanceOf(java.io.IOException.class);
        assertThat(db.queryForObject("select count(*) from summer_file_entry_creation", Integer.class)).isOne();
        mvc.perform(post(filesApi + "/folders").param("folder", "子目录").param("name","空目录").with(admin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(delete(filesApi + "/entries").param("key","子目录/").with(admin).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete(filesApi + "/entries").param("key","子目录/空目录/").with(user("unassigned")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(filesApi + "/entries").param("key","子目录/空目录/").with(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deleted").value(true));
        assertThat(root.resolve("attachments/子目录/空目录")).doesNotExist();
        assertThat(db.queryForObject("select count(*) from summer_file_entry_creation", Integer.class)).isOne();
        mvc.perform(delete(filesApi + "/entries").param("key","/").with(admin).with(csrf())).andExpect(status().isBadRequest());
        files.write(id, "archive/附件.bin", new ByteArrayInputStream(content), content.length);
        assertThatThrownBy(() -> files.write(id, "archive/附件.bin", new ByteArrayInputStream(content), 4))
                .isInstanceOf(java.io.IOException.class);
        try (var stream = files.read(id, "archive/附件.bin")) { assertThat(stream.readAllBytes()).isEqualTo(content); }
        mvc.perform(get(API).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].blobCount").value(1))
                .andExpect(jsonPath("$.data[0].totalSize").value(3));
        assertThatThrownBy(() -> files.delete(id, "../outside")).isInstanceOf(RuntimeException.class);
        files.delete(id, "archive/附件.bin");
        assertThat(files.list().getFirst().blobCount()).isZero();
        files.write(id, "keep", new ByteArrayInputStream(content), content.length);
        mvc.perform(delete(API + "/" + id).with(admin).with(csrf())).andExpect(status().isOk());
        assertThat(files.list()).isEmpty();
        var restored = files.register("重新登记", FileStoreManager.Type.LOCAL, "attachments");
        var restoredEntries = files.entries(restored.id(), "");
        assertThat(restoredEntries).filteredOn(entry -> entry.name().equals("子目录")).singleElement()
                .satisfies(entry -> assertThat(entry.createdBy()).isEqualTo("summer-user"));
        assertThat(restoredEntries).filteredOn(entry -> entry.name().equals("keep")).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.createdBy()).isNull();
                    assertThat(entry.createdAt()).isNull();
                });
        assertThat(Files.readAllBytes(root.resolve("attachments/keep"))).isEqualTo(content);
    }
}
