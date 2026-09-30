# Summer 4 #78 请求体日志复核

## 需求与边界

- 追溯：Issue #78、#70 的 TS-MVC、ADR 0020/0021。
- `summer.mvc.request-logging.body.enabled` 默认 `false`；开启后只对 MVC 已缓存的请求正文生成日志副本。requestId、方法、路径、状态、耗时仍在同一 `RequestLogRecord` 中。
- 仅可解析的 JSON 对象或对象数组支持预览。按配置字段名递归脱敏，再按 `max-preview-length` 截断。文本、无类型、其他类型、JSON 标量/标量数组及畸形 JSON 返回 `[BODY_UNAVAILABLE]`。容器管理的表单与 multipart 未缓存，预览为空。以上路径不影响 Controller 完整读取。
- 脱敏只保证配置字段名匹配的 JSON 属性。未列入字段名的敏感值不能自动识别；应用必须配置字段名并避免把凭据放在其他字段中。日志预览上限不是请求接收上限，后者由 #73 独立验收。

## 复核证据

- 红灯：新增 `RequestLoggingFilterTest.unstructuredBodiesNeverEnterTheLogPreview` 后，原实现的文本明文预览使 7 项聚焦测试中 1 项失败。
- 绿灯：聚焦测试验证默认关闭、脱敏和截断、requestId、完整业务读取、文本/无类型/其他类型、JSON 标量及畸形、表单和 MockMvc Controller 路径。`./gradlew check --rerun-tasks` 在初次安全修正后通过；补充对象数组与 MVC 畸形 JSON 后再次 `./gradlew check` 通过。变更 rebase 到本地 `summer4` 的 #82 后复跑 `./gradlew check`，最终 XML 汇总 151 tests、0 failures、0 errors、0 skipped，模块边界检查通过。
- 审阅：变更仅在 MVC 快照逻辑、对应 HTTP 测试及说明文档；没有修改缓存接收限制、日志 sink 或公共配置默认值。`git diff --check` 通过。

## 回滚

本票作为独立提交时可用 `git revert <commit>` 回退；回退会恢复文本明文预览风险，生产环境应保持 `body.enabled=false`，直到重新引入安全修正。#73 的接收限制须单独验证和回滚。
