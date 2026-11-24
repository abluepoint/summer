# 通过菜单管理配置访问控制目录

Web 客户端的“访问控制”使用平台菜单管理的父子关系，按顺序包含接口资源、权限管理、菜单管理和平台角色与权限。导航按已授权的子项展示目录。

菜单清单可单独配置 `platform/roles` 的名称、所属客户端、父菜单及授权。该项存在时，平台角色页面使用该项的资格，租户角色页面继续使用 `roles`。旧清单沿用 [ADR 0042](0042-separate-platform-and-tenant-management-entries.md) 的角色入口映射。

本决定替代 [ADR 0044](0044-group-platform-roles-under-access-control.md) 的前端分组方式。在既有平台角色菜单权限上关联新菜单项，可以保留原有角色的访问资格。
