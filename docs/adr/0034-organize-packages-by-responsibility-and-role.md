# 按职责和角色组织包结构

Summer 4 在 ADR 0002 的能力、装配与依赖入口分离之内，进一步按职责组织外层 Java 包：框架能力在职责内部区分 api、spi、internal；应用简单 CRUD 与直接查询采用技术分层，具有业务策略或状态约束的复杂功能采用 api/application/domain/infrastructure 领域分层。领域依赖指向业务模型与契约，Domain 不依赖 API、Application 或 Infrastructure；项目接受更多包和显式跨层映射带来的成本，以明确扩展边界、业务规则归属与技术适配责任。

安全扩展 Store 接口及默认运行时实现归 summer-security，自动配置模块只负责绑定、选择与装配。一次分层只建立有实际职责的包和类型，不为分层复制同构输入，不改变既有框架与 AICT 产品边界。2026-10-28 已确认的逐模块目标与命名规则见 [包结构与命名规范](../architecture/包结构与命名规范.md)；当前仅维护文档，源码迁移尚未实施。
