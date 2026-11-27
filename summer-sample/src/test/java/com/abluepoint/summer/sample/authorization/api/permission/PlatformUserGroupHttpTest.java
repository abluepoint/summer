package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.abluepoint.summer.sample.authorization.application.permission.*;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform",
    "spring.application.name=platform-user-group-test"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class PlatformUserGroupHttpTest {
  private static final String PATH = "/api/v1/platform/user-groups";
  private static final String ADMIN = "summer-user";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ManagedInterfaceResources resources;
  @Autowired PermissionResourceAssociations associations;
  @Autowired MenuPublicationState publication;
  @Autowired PlatformTransactionManager transactions;

  private long version() {
    return db.queryForObject("select revision from summer_authorization_domain where domain_key='PLATFORM:platform'", Long.class);
  }

  private void write(MockHttpServletRequestBuilder req,String body,long version,int expected) throws Exception {
    mvc.perform(req.with(user(ADMIN)).with(csrf()).header("If-Match","\""+version+"\"")
        .contentType("application/json").content(body)).andExpect(status().is(expected));
  }

  @Test
  void administratorAssignmentsGrantCurrentGroupRolesAndIgnoreRetiredMemberships() throws Exception {
    db.execute("create table if not exists summer_platform_directory_install(id int primary key)");
    var installer = new PlatformDirectoryInstallation(db,resources,associations,publication);
    new TransactionTemplate(transactions).executeWithoutResult(tx -> installer.install());
    new TransactionTemplate(transactions).executeWithoutResult(tx -> installer.installUserGroups());
    var role = db.queryForObject("select role_id from summer_permission_user_role where username=? and domain_key='PLATFORM:platform'", String.class, ADMIN);
    for (String username : java.util.List.of("group-person", "group-colleague")) {
      db.update("""
          insert into summer_auth_user(username,password_hash,role,active,created_at,updated_at)
          select ?,password_hash,role,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP from summer_auth_user where username=?
          """, username, ADMIN);
      db.update("insert into summer_platform_administrator(username,display_name) values (?,?)", username, username);
    }
    var body = "{\"name\":\"运维组\",\"roles\":[{\"roleId\":\""+role+"\"}]}";
    String members = "\"members\":[{\"username\":\"group-person\"}]";
    long before = version();
    write(post(PATH),"{\"name\":\"旧版请求\","+members+"}",before,400);
    write(post(PATH),body,before,200);
    var id = db.queryForObject("select id from summer_platform_user_group where name='运维组'",String.class);
    // Upgraded databases can still hold membership history from V200.
    db.execute("create table if not exists summer_platform_group_member(group_id varchar(64),username varchar(128))");
    db.update("insert into summer_platform_group_member(group_id,username) values (?,?)",id,"group-person");
    mvc.perform(get(PATH).with(user("group-person"))).andExpect(status().isForbidden());
    write(put(PATH+"/"+id),"{\"operation\":\"MEMBERS\"}",version(),400);
    write(put(PATH+"/"+id),"{\"name\":\"旧版更新\","+members+"}",version(),400);
    write(put(PATH+"/"+id),"{\"operation\":\"ROLES\","+members+"}",version(),400);
    write(put(PATH+"/"+id),"{\"name\":\"stale\"}",before,409);
    write(put(PATH+"/"+id),body.replace(role,"unknown-role"),version(),400);
    write(put(PATH+"/"+id),"{\"operation\":\"DETAILS\",\"name\":\"运维组\",\"description\":\"运维值班\"}",version(),200);
    mvc.perform(get(PATH).with(user(ADMIN))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.people").doesNotExist())
        .andExpect(jsonPath("$.data.groups[0].members").doesNotExist())
        .andExpect(jsonPath("$.data.groups[0].roles[0]").value(role))
        .andExpect(jsonPath("$.data.groups[0].description").value("运维值班"));
    write(put(PATH+"/"+id),"{\"operation\":\"ROLES\"}",version(),200);
    mvc.perform(get(PATH).with(user(ADMIN))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.groups[0].roles").isEmpty());
    write(put(PATH+"/"+id),"{\"operation\":\"ROLES\",\"roles\":[{\"roleId\":\""+role+"\"}]}",version(),200);
    mvc.perform(get(PATH).with(user("group-person"))).andExpect(status().isForbidden());
    String roster = "/api/v1/platform/administrators";
    String assign = roster + "/user-groups/" + id;
    String selection = "{\"administrators\":[{\"username\":\"group-person\"}]}";
    mvc.perform(get(roster).with(user(ADMIN))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.groups[0].administrators").isEmpty());
    mvc.perform(put(assign).with(user("group-person")).with(csrf()).contentType("application/json")
        .header("If-Match","\""+version()+"\"").content(selection)).andExpect(status().isForbidden());
    long rosterVersion = version();
    String twoAdministrators = """
        {"administrators":[{"username":"group-person"},{"username":"group-colleague"}]}
        """;
    write(put(assign),twoAdministrators,rosterVersion,200);
    mvc.perform(get(roster).with(user(ADMIN))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.groups[0].administrators",
            org.hamcrest.Matchers.containsInAnyOrder("group-person", "group-colleague")));
    mvc.perform(get(PATH).with(user("group-colleague"))).andExpect(status().isOk());
    mvc.perform(get(PATH).with(user("group-person"))).andExpect(status().isOk());
    write(put(assign),selection,version(),200);
    mvc.perform(get(PATH).with(user("group-colleague"))).andExpect(status().isForbidden());
    write(put(assign),"{}",rosterVersion,409);
    write(put(assign),selection.replace("group-person","not-on-roster"),version(),400);
    mvc.perform(get(roster).with(user(ADMIN))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.groups[0].administrators[0]").value("group-person"));
    mvc.perform(get(PATH).with(user("group-person"))).andExpect(status().isOk());
    write(put(PATH+"/"+id),"{\"operation\":\"ROLES\"}",version(),200);
    mvc.perform(get(PATH).with(user("group-person"))).andExpect(status().isForbidden());
    write(put(PATH+"/"+id),"{\"operation\":\"ROLES\",\"roles\":[{\"roleId\":\""+role+"\"}]}",version(),200);
    write(post(PATH),body.replace("运维组","第二组"),version(),200);
    var second = db.queryForObject("select id from summer_platform_user_group where name='第二组'",String.class);
    write(put(roster+"/user-groups/"+second),selection,version(),200);
    write(put(assign),"{}",version(),200);
    mvc.perform(get(PATH).with(user("group-person"))).andExpect(status().isOk());
    write(delete(PATH+"/"+second),"",version(),200);
    mvc.perform(get(PATH).with(user("group-person"))).andExpect(status().isForbidden());
    mvc.perform(get(roster).with(user(ADMIN))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.groups[0].administrators").isEmpty());
    write(delete(PATH+"/"+id),"",version(),200);
    assertEquals(0,db.queryForObject("select count(*) from summer_platform_group_role",Integer.class));
  }
}
