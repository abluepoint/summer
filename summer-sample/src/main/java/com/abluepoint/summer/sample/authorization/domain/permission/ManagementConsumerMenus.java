package com.abluepoint.summer.sample.authorization.domain.permission;

import java.util.List;
import java.util.Set;

/** Fresh, unpublished management page metadata. MENU entries never grant API access. */
public final class ManagementConsumerMenus {
  public static final Set<String> PLATFORM_DICTIONARY_PAGES =
      Set.of("dictionary/manage", "dictionary/groups", "dictionary/divisions");

  public record Page(String id, String title) {}

  public static final Page FILE_DIRECTORY = new Page("platform-management-files", "文件管理");
  public static final Page FILE_REPOSITORIES = new Page("platform/file-repositories", "文件存储库");
  public static final Page FILE_MANAGER = new Page("platform/files", "文件管理器");

  public static String platformParent(String id) {
    if (FILE_REPOSITORIES.id().equals(id) || FILE_MANAGER.id().equals(id)) return FILE_DIRECTORY.id();
    return id.startsWith("platform/") ? "platform-management" : null;
  }

  public static final List<Page> PLATFORM_DIRECTORY_PAGES = List.of(
      new Page("platform-management","平台管理"),
      new Page("platform/administrators","平台管理员管理"),
      new Page("platform/organization","平台组织机构管理"));

  public static final List<Page> PAGES = List.of(
      new Page("roles", "角色与权限"),
      new Page("permissions", "权限管理"),
      new Page("menu-manifest", "菜单管理"),
      new Page("interface-resources", "接口资源"),
      new Page("oauth-clients", "OAuth 客户端"),
      new Page("auth-users", "身份管理"),
      new Page("organization", "组织与成员"));

  private ManagementConsumerMenus() {}

  public static boolean contains(String id) {
    return PAGES.stream().anyMatch(page -> page.id().equals(id));
  }
}
