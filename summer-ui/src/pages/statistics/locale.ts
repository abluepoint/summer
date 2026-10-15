export default {
  'zh-CN': {
    'statistics.tenant': '活动租户',
    'statistics.chooseTenant': '请选择一个活动成员租户。',
    'statistics.unavailable': '无权读取',
    'statistics.noMetrics': '当前没有可读取的业务指标。',
    'statistics.snapshot': '库存快照时间',
    'statistics.scopeNote': '用户按企业成员主体去重；用户、字典和审计受当前组织限制，字典同时应用有效数据范围。角色与任务为租户级；库存包括各状态。',
    'statistics.formula': '任务成功率 = 窗口内成功执行数 ÷ 全部执行数（含运行中及失败）；按执行 ID 去重，重试不另计，无执行时为 0%。',
    'statistics.windowNote': '时间窗口仅用于执行开始时间及审计发生时间，包含起点、不含终点，最多 366 天。',
    'statistics.interfaceNote': '接口数为当前实时授权的 MVC 接口资源，按 HTTP 方法与路径去重；不含统计自身，调用仍需满足接口的其他约束。',
  },
  'en-US': {
    'statistics.tenant': 'Active enterprise',
    'statistics.chooseTenant': 'Select an enterprise with an active membership.',
    'statistics.unavailable': 'Access denied',
    'statistics.noMetrics': 'No business metrics are readable with the current permissions.',
    'statistics.snapshot': 'Inventory snapshot time',
    'statistics.scopeNote': 'Users are distinct enterprise member subjects. Users, dictionaries and audit events follow the current organization; dictionaries also follow effective data scope. Roles and tasks are enterprise-wide. Inventory includes all statuses.',
    'statistics.formula': 'Task success rate = successful executions ÷ all executions in the window (including running and failed). Count distinct execution IDs; retries do not add executions. No executions means 0%.',
    'statistics.windowNote': 'The window applies only to execution start and audit occurrence times: inclusive start, exclusive end, at most 366 days.',
    'statistics.interfaceNote': 'Interfaces are currently authorized MVC resources, deduplicated by HTTP method and path, excluding statistics. Other endpoint constraints still apply to actual calls.',
  },
};
