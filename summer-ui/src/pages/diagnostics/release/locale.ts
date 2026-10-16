export default {
  'zh-CN': {
    title: '升级预检', refresh: '重新检查', checking: '正在检查', ready: '运行条件就绪', notReady: '运行条件未就绪',
    boundary: '本页检查当前节点的运行条件。发布验收还需核对迁移兼容性、备份恢复、双节点滚动升级和业务流程的实际演练证据。',
    external: '待独立验证：expand/contract、关键数据与权限恢复、API 与会话兼容窗口、缓存与事件投递、实时连接重放。',
    error: '升级预检加载失败，请重试。', forbidden: '没有查看升级预检的权限。', retry: '重试',
    item: '检查项', result: '结果', detail: '检查说明', passed: '通过', failed: '未通过', timestamp: '检查时间',
    database: '数据库', migrations: 'Flyway 迁移', configuration: '持久化配置', keys: '签名密钥', authorization: '权限数据',
    tasks: '集群任务', cache: '共享缓存', events: '事件处理', realtime: '实时连接', plugins: '插件兼容性',
  },
  'en-US': {
    title: 'Upgrade preflight', refresh: 'Check again', checking: 'Checking', ready: 'Runtime prerequisites ready', notReady: 'Runtime prerequisites not ready',
    boundary: 'This page checks the current node’s runtime prerequisites. Release acceptance also requires actual migration compatibility, backup and restore, rolling upgrade and business flow evidence.',
    external: 'Separate evidence required: expand/contract, key data and permission restore, API and session compatibility, cache and event delivery, and realtime replay.',
    error: 'Upgrade preflight could not be loaded. Please retry.', forbidden: 'You do not have permission to view upgrade preflight.', retry: 'Retry',
    item: 'Check', result: 'Result', detail: 'Description', passed: 'Passed', failed: 'Failed', timestamp: 'Checked at',
    database: 'Database', migrations: 'Flyway migrations', configuration: 'Persistent configuration', keys: 'Signing keys', authorization: 'Permission data',
    tasks: 'Clustered tasks', cache: 'Shared cache', events: 'Event processing', realtime: 'Realtime connections', plugins: 'Plugin compatibility',
  },
};
