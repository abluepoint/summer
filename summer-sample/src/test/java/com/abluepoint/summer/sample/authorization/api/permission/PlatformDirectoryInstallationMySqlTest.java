package com.abluepoint.summer.sample.authorization.api.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.abluepoint.summer.sample.authorization.infrastructure.permission.PlatformDirectoryInstallation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/mysql",
    "spring.jpa.hibernate.ddl-auto=none"
})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformDirectoryInstallationMySqlTest {
  @Container @ServiceConnection static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

  @Autowired PlatformDirectoryInstallation installation;
  @Autowired JdbcTemplate db;

  @Test
  void firstScanPublishesBothMenuEntriesInOneInstallation() {
    db.update("insert into summer_authorization_domain(domain_key,engine_mode,revision)"
        + " values ('PLATFORM:platform','MANAGED',1) on duplicate key update engine_mode='MANAGED'");
    db.update("update summer_menu_publication set published=true,manifest_version=13"
        + " where publication_id='global'");
    assertThat(db.queryForObject("select count(*) from summer_interface_resource"
        + " where handler_provenance like '%PlatformDirectoryController@%'", Integer.class))
        .isZero();

    installation.install();
    installation.install();

    assertThat(db.queryForList("select menu_id from summer_menu_manifest_item"
        + " where parent_menu_id='platform-management' and manifest_version=14", String.class))
        .contains("platform/administrators", "platform/organization");
    assertThat(db.queryForObject("select manifest_version from summer_menu_publication"
        + " where publication_id='global'", Long.class)).isEqualTo(14L);

    installation.installFileRepositories();
    installation.installFileRepositories();
    assertThat(db.queryForList("select menu_id from summer_menu_manifest_item"
        + " where parent_menu_id='platform-management-files' and manifest_version=15", String.class))
        .containsExactly("platform/file-repositories");
    assertThat(db.queryForObject("select count(*) from summer_permission_resource"
        + " where domain_key='PLATFORM:platform' and permission_id='file-repositories-manage'", Integer.class))
        .isEqualTo(3);

    installation.installFiles();
    installation.installFiles();
    assertThat(db.queryForList("select menu_id from summer_menu_manifest_item"
        + " where parent_menu_id='platform-management-files' and manifest_version=16", String.class))
        .contains("platform/files", "platform/file-repositories");
    assertThat(db.queryForObject("select count(*) from summer_permission_resource"
        + " where domain_key='PLATFORM:platform' and permission_id='files-manage'", Integer.class))
        .isEqualTo(3);

    // Simulate upgrading an installation with the original two write endpoints.
    String removeDeleteBinding = """
        delete b from summer_permission_resource b join summer_interface_resource r on b.resource_id=r.resource_id
        where b.permission_id='files-manage' and r.normalized_path='/api/v1/platform/files/{id}/entries'
        and r.http_method='DELETE'
        """;
    assertThat(db.update(removeDeleteBinding)).isOne();
    db.update("delete from summer_file_repository_install where id=5");
    installation.installFiles();
    assertThat(db.update(removeDeleteBinding)).isOne();
    installation.installFiles();
    assertThat(db.update(removeDeleteBinding)).isZero();

    // Recreate the persisted pre-rename handler signatures and an explicitly revoked create grant.
    db.update("delete from summer_file_repository_install where id=3");
    db.update("""
        delete b from summer_permission_resource b join summer_interface_resource r on b.resource_id=r.resource_id
        where r.normalized_path='/api/v1/platform/file-repositories' and r.http_method='POST'
        """);
    db.update("""
        update summer_interface_resource set handler_provenance=replace(handler_provenance,'FileStoreManager$','FileManager$'),
          handler_hash='pre-rename' where normalized_path='/api/v1/platform/file-repositories'
        """);
    installation.installFileRepositories();
    String activeBindings = """
        select r.http_method from summer_permission_resource b join summer_interface_resource r on b.resource_id=r.resource_id
        where r.active=true and r.normalized_path='/api/v1/platform/file-repositories' and b.domain_key='PLATFORM:platform'
        """;
    assertThat(db.queryForList(activeBindings, String.class)).containsExactly("GET");
    installation.installFileRepositories();
    assertThat(db.queryForList(activeBindings, String.class)).containsExactly("GET");
  }
}
