## Agent skills

### Issue tracker

Issues and PRDs are tracked in GitHub Issues. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the canonical five-label triage vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repository using root `CONTEXT.md` and `docs/adr/`. See `docs/agents/domain.md`.

### 服务端请求校验

新增或修改 Spring MVC 接口时，优先用 Summer 的 `@Field` 和具名 Style 声明字段类型、格式、范围、必填及可空性，对象数组可用 `@FieldList`。启用前核对所有合法的路径、查询和正文字段，包含别名和可选参数。跨字段关系、权限、数据库状态及并发版本检查放在业务层。
