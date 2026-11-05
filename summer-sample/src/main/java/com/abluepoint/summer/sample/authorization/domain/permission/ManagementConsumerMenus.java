package com.abluepoint.summer.sample.authorization.domain.permission;

import java.util.List;

/** Fresh, unpublished management page metadata. MENU entries never grant API access. */
public final class ManagementConsumerMenus {
  public record Page(String id, String title) {}

  public static final List<Page> PAGES = List.of(
      new Page("roles", "角色与权限"),
      new Page("permissions", "权限管理"),
      new Page("menu-manifest", "菜单管理"),
      new Page("interface-resources", "接口目录"),
      new Page("oauth-clients", "OAuth 客户端"),
      new Page("auth-users", "身份管理"),
      new Page("organization", "组织与成员"),
      new Page("authorization-events", "授权事件"),
      new Page("data-scopes", "数据权限"));

  private ManagementConsumerMenus() {}

  public static boolean contains(String id) {
    return PAGES.stream().anyMatch(page -> page.id().equals(id));
  }
}
