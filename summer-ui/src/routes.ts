import { AuthParams } from '@/utils/authentication';
import { useMemo } from 'react';
import { createMenuManifest } from '@/menu/manifest';
import { resolveMenuRoutes, useMenuItems } from '@/menu/access';

export type IRoute = AuthParams & {
  name: string;
  key: string;
  // Local group routes must not prefix-match disabled descendants.
  exact?: boolean;
  iconKey?: string;
  capabilityCode?: string;
  // 当前页是否展示面包屑
  breadcrumb?: boolean;
  children?: IRoute[];
  // 当前路由是否渲染菜单项，为 true 的话不会在菜单中显示，但可通过路由地址访问。
  ignore?: boolean;
};

const officialExampleRoutes: IRoute[] = [
  {
    name: 'menu.welcome',
    key: 'welcome',
  },
  {
    name: 'menu.dashboard',
    key: 'dashboard',
    children: [
      {
        name: 'menu.dashboard.workplace',
        key: 'dashboard/workplace',
      },
      {
        name: 'menu.dashboard.monitor',
        key: 'dashboard/monitor',
        requiredPermissions: [
          { resource: 'menu.dashboard.monitor', actions: ['write'] },
        ],
      },
      {
        name: 'menu.statistics',
        key: 'statistics',
        capabilityCode: 'enterprise.statistics.read',
      },
    ],
  },
  {
    name: 'menu.visualization',
    key: 'visualization',
    children: [
      {
        name: 'menu.visualization.dataAnalysis',
        key: 'visualization/data-analysis',
        requiredPermissions: [
          { resource: 'menu.visualization.dataAnalysis', actions: ['read'] },
        ],
      },
      {
        name: 'menu.visualization.multiDimensionDataAnalysis',
        key: 'visualization/multi-dimension-data-analysis',
        requiredPermissions: [
          {
            resource: 'menu.visualization.dataAnalysis',
            actions: ['read', 'write'],
          },
          {
            resource: 'menu.visualization.multiDimensionDataAnalysis',
            actions: ['write'],
          },
        ],
        oneOfPerm: true,
      },
    ],
  },
  {
    name: 'menu.list',
    key: 'list',
    children: [
      {
        name: 'menu.list.searchTable',
        key: 'list/search-table',
      },
      {
        name: 'menu.list.cardList',
        key: 'list/card',
      },
    ],
  },
  {
    name: 'menu.form',
    key: 'form',
    children: [
      {
        name: 'menu.form.group',
        key: 'form/group',
        requiredPermissions: [
          { resource: 'menu.form.group', actions: ['read', 'write'] },
        ],
      },
      {
        name: 'menu.form.step',
        key: 'form/step',
        requiredPermissions: [
          { resource: 'menu.form.step', actions: ['read'] },
        ],
      },
    ],
  },
  {
    name: 'menu.profile',
    key: 'profile',
    children: [
      {
        name: 'menu.profile.basic',
        key: 'profile/basic',
      },
    ],
  },

  {
    name: 'menu.result',
    key: 'result',
    children: [
      {
        name: 'menu.result.success',
        key: 'result/success',
        breadcrumb: false,
      },
      {
        name: 'menu.result.error',
        key: 'result/error',
        breadcrumb: false,
      },
    ],
  },
  {
    name: 'menu.exception',
    key: 'exception',
    children: [
      {
        name: 'menu.exception.403',
        key: 'exception/403',
      },
      {
        name: 'menu.exception.404',
        key: 'exception/404',
      },
      {
        name: 'menu.exception.500',
        key: 'exception/500',
      },
    ],
  },
  {
    name: 'menu.user',
    key: 'user',
    children: [
      {
        name: 'menu.user.info',
        key: 'user/info',
      },
      {
        name: 'menu.user.setting',
        key: 'user/setting',
      },
    ],
  },
];

/**
 * Summer-owned navigation stays first. The official routes are kept intact
 * below one menu group, while their public paths remain unchanged so direct
 * links from the upstream baseline continue to work.
 */
export const routes: IRoute[] = [
  { name: 'menu.permissions', key: 'permissions', capabilityCode: 'enterprise.roles.read', requiredPermissions: [{ resource: 'enterprise.roles', actions: ['read'] }] },
  {
    name: 'menu.oauthClients',
    key: 'oauth-clients',
    requiredPermissions: [{ resource: 'menu.oauthClients', actions: ['read'] }],
    capabilityCode: 'security.oauth-client.read',
  },
  {
    name: 'menu.plugins.customerInsights',
    key: 'plugins/customer-insights',
    exact: true,
    capabilityCode: 'enterprise.plugins.customer-insights.read',
    requiredPermissions: [{ resource: 'enterprise.plugins.customer-insights', actions: ['read'] }],
  },
  {
    name: 'menu.plugins',
    key: 'plugins',
    requiredPermissions: [{ resource: 'menu.plugins', actions: ['read'] }],
    capabilityCode: 'platform.plugins.read',
  },
  {
    name: 'menu.sessions',
    key: 'sessions',
    requiredPermissions: [{ resource: 'menu.sessions', actions: ['read'] }],
    capabilityCode: 'platform.capabilities.read',
  },
  {
    name: 'menu.securityKeys',
    key: 'security-keys',
    requiredPermissions: [{ resource: 'menu.securityKeys', actions: ['read'] }],
  },
  {
    name: 'menu.interfaceResources',
    key: 'interface-resources',
    requiredPermissions: [
      { resource: 'platform.interface-resources', actions: ['read'] },
    ],
    capabilityCode: 'platform.interface-resources.read',
  },
  {
    name: 'menu.audit',
    key: 'audit',
    requiredPermissions: [{ resource: 'enterprise.audit', actions: ['read'] }],
    capabilityCode: 'enterprise.audit.read',
  },
  {
    name: 'menu.systemParameters',
    key: 'system-parameters',
    requiredPermissions: [
      { resource: 'menu.systemParameters', actions: ['read'] },
    ],
  },
  {
    name: 'menu.authUsers',
    key: 'auth-users',
    requiredPermissions: [
      { resource: 'enterprise.members', actions: ['read'] },
    ],
    capabilityCode: 'enterprise.members.read',
  },
  {
    name: 'menu.organization',
    key: 'organization',
    requiredPermissions: [
      { resource: 'enterprise.organization', actions: ['read'] },
    ],
    capabilityCode: 'enterprise.organization.read',
  },
  {
    name: 'menu.roles',
    key: 'roles',
    requiredPermissions: [{ resource: 'enterprise.roles', actions: ['read'] }],
    capabilityCode: 'enterprise.roles.read',
  },
  {
    name: 'menu.authorizationEvents',
    key: 'authorization-events',
    requiredPermissions: [
      { resource: 'menu.authorizationEvents', actions: ['read'] },
    ],
    capabilityCode: 'enterprise.roles.read',
  },
  {
    name: 'menu.tasks',
    key: 'tasks',
    requiredPermissions: [{ resource: 'enterprise.tasks', actions: ['read'] }],
    capabilityCode: 'enterprise.tasks.read',
  },
  {
    name: 'menu.messages',
    key: 'messages',
    requiredPermissions: [
      { resource: 'enterprise.messages', actions: ['read'] },
    ],
    capabilityCode: 'enterprise.messages.read',
  },
  {
    name: 'menu.dataScopes',
    key: 'data-scopes',
    requiredPermissions: [{ resource: 'menu.dataScopes', actions: ['read'] }],
  },
  {
    name: 'menu.menuManifest',
    key: 'menu-manifest',
    requiredPermissions: [{ resource: 'menu.menuManifest', actions: ['read'] }],
  },
  {
    name: 'menu.dictionary',
    key: 'dictionary',
    children: [
      {
        name: '字典管理',
        key: 'dictionary/manage',
        capabilityCode: 'platform.dictionary.read',
        requiredPermissions: [{ resource: 'platform.dictionary', actions: ['read'] }],
      },
      {
        name: 'menu.dictionary.form',
        key: 'dictionary/form',
      },
    ],
  },
  {
    name: 'menu.diagnostics',
    key: 'diagnostics',
    children: [
      {
        name: 'menu.diagnostics.redis',
        key: 'diagnostics/redis',
        capabilityCode: 'platform.capabilities.read',
        requiredPermissions: [
          { resource: 'platform.capabilities', actions: ['read'] },
        ],
      },
      {
        name: 'menu.diagnostics.runtime',
        key: 'diagnostics/runtime',
        capabilityCode: 'platform.capabilities.read',
        requiredPermissions: [
          { resource: 'platform.capabilities', actions: ['read'] },
        ],
      },
      {
        name: 'menu.diagnostics.release',
        key: 'diagnostics/release',
        capabilityCode: 'internal.release.read',
        requiredPermissions: [
          { resource: 'internal.release', actions: ['read'] },
        ],
      },
      {
        name: 'menu.diagnostics.resilience',
        key: 'diagnostics/resilience',
        requiredPermissions: [
          { resource: 'menu.diagnostics.resilience', actions: ['read'] },
        ],
      },
    ],
  },
  {
    name: 'menu.customer',
    key: 'customer',
    children: [
      {
        name: 'menu.customer.drawer',
        key: 'customer/drawer',
      },
      {
        name: 'menu.customer.modal',
        key: 'customer/modal',
      },
      {
        name: 'menu.customer.page',
        key: 'customer/page',
      },
    ],
  },
  {
    name: 'customer.detail',
    key: 'customer/detail',
    ignore: true,
    breadcrumb: false,
  },
  {
    name: 'menu.oauth2Callback',
    key: 'oauth2/callback',
    ignore: true,
    breadcrumb: false,
  },
  {
    name: 'menu.arcoExamples',
    key: 'arco-examples',
    children: officialExampleRoutes,
  },
];

export const menuManifest = createMenuManifest(routes);

export const getName = (path: string, routes) => {
  return routes.find((item) => {
    const itemPath = `/${item.key}`;
    if (path === itemPath) {
      return item.name;
    } else if (item.children) {
      return getName(path, item.children);
    }
  });
};

export const generatePermission = (role: string) => {
  const actions = role === 'admin' ? ['*'] : ['read'];
  const result = {};
  const visit = (items: IRoute[]) => {
    items.forEach((item) => {
      if (item.children?.length) {
        visit(item.children);
      } else {
        result[item.name] = actions;
      }
    });
  };
  visit(routes);
  return result;
};

const useRoute = (userPermission): [IRoute[], string] => {
  const menuItems = useMenuItems();
  const permissionRoute = useMemo(
    () => resolveMenuRoutes(routes, menuItems),
    [menuItems]
  );

  const defaultRoute = useMemo(() => {
    const first = permissionRoute[0];
    if (first) {
      const firstRoute = first?.children?.[0]?.key || first.key;
      return firstRoute;
    }
    return '';
  }, [permissionRoute]);

  return [permissionRoute, defaultRoute];
};

export default useRoute;
