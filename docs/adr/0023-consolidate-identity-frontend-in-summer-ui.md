# 将身份管理前端统一到 Summer UI

## 背景

`summer-ui/` 已采用官方 Arco Design Pro React/Vite 基线，并承接 Summer 4 的浏览器登录与后续业务页面。工作区曾同时准备 `apps/aict-identity/` 独立原型及以 Vben 独立产品应用为前提的 AICT 规格，这会形成两套前端入口、路由、构建与交付口径。2026-09-30 用户明确要求放弃 `aict-identity`，改用 `summer-ui` 实现。

## 决策

1. 仓库只以 `summer-ui/` 作为当前前端应用入口。身份管理页面和后续 AICT 前端纵向模块在该应用内实现；不再创建或交付 `apps/aict-identity/`。
2. 新页面使用现有 Arco Pro 的路由、布局、构建与测试基线。产品域可在应用内部保持清晰路由和模块边界，但不以第二套前端工程实现隔离。
3. 本决策替代 AICT 规格中“建立独立 `aict-identity` / Vben 产品子应用”的前端工程选择。企业、成员、授权、Agent、MCP、审计等业务需求及服务端归属仍需按各自契约实施，不能因前端合并而视为已经交付。
4. ADR 0014 与 0015 已确定的 Arco Pro 基线及当前 Summer Sample 的 public-client PKCE 链路继续有效。AICT 规格中的同源 BFF、Product Session 与机密客户端设计涉及另一组安全边界；将其接入同一前端前，须单独明确与现有 PKCE 链路的接口和适用范围，不能仅因共用 UI 就暗中替换或混用两套认证语义。

## 后果与验收

- 前端部署、依赖和 UI 基线统一在 `summer-ui/`；旧独立原型不进入后续提交。
- AICT 规格中涉及 Vben、Vue Query、Pinia、独立构建与部署的文字不再代表当前前端实施方案，后续修订须保留业务要求并标出替代关系。
- 以 `summer-ui` 的类型检查、lint、构建、组件/浏览器测试和真实后端契约作为前端验收证据；页面存在或原型可渲染不等于业务闭环完成。
- 本 ADR 只确定前端归属，不宣称 AICT 全量功能或安全架构已经实现。
